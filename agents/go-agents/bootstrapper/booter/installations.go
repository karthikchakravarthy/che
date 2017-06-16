//
// Copyright (c) 2012-2017 Codenvy, S.A.
// All rights reserved. This program and the accompanying materials
// are made available under the terms of the Eclipse Public License v1.0
// which accompanies this distribution, and is available at
// http://www.eclipse.org/legal/epl-v10.html
//
// Contributors:
//   Codenvy, S.A. - initial API and implementation
//

package booter

import (
	"fmt"
	"github.com/eclipse/che/agents/go-agents/core/process"
	"net"
	"strings"
	"time"
)

// installation defines procedure for installer
type installation interface {
	// preCheck performs all necessary checks before the installation process is executed,
	// if there is no errors occurred while check then installation could be performed
	preCheck() error
	// execute performs the installer installation
	// installation successful only if there is not errors occurred while installation
	execute() error
}

// scriptInst executes installer script
// if script execution failed or the time limit exceeded then installation considered failed
// otherwise the installation is successful
type scriptInst struct {
	// installer defines the necessary set of properties for installation
	installer Installer
	// timeLimit defines the time limit for installation
	timeLimit time.Duration
}

func (sci *scriptInst) preCheck() error { return nil }

func (sci *scriptInst) execute() error {
	_, diedC, err := executeScript(sci.installer)
	if err != nil {
		fmt.Errorf("Scrip execution failed Error: %s", err)
	}
	select {
	case died := <-diedC:
		if died.ExitCode != 0 {
			return fmt.Errorf(
				"Exit code for Installer '%s' installation is '%d' while should be 0",
				sci.installer.ID,
				died.ExitCode,
			)
		}
	case <-time.After(sci.timeLimit):
		return fmt.Errorf("Timeout reached before installation of '%s' completed", sci.installer.ID)
	}
	return nil
}

// serverInst executes installer script and waits all the installer servers
// if script execution failed or exceeds the time limit then installation considered failed
// otherwise the installation is successful
type serverInst struct {
	// installer defines the necessary set of properties for installation
	installer Installer
	// period defines period between servers checks
	period time.Duration
	// timeLimit defines the maximum time limits for the installation
	timeLimit time.Duration
}

// preCheck performs pre installation checks
func (svi *serverInst) preCheck() error {
	for _, server := range svi.installer.Servers {
		if tryConn(server) == nil {
			return fmt.Errorf("server address 'localhost:%s' already in use", server.Port)
		}
	}
	return nil
}

func (svi *serverInst) execute() error {
	pid, diedC, err := executeScript(svi.installer)
	if err != nil {
		fmt.Errorf("Scrip execution failed Error: %s", err)
	}
	checker := &dialChecker{svi.period, make(chan bool, 1)}
	select {
	case <-checker.allAvailable(svi.installer.Servers):
		process.RemoveSubscriber(pid, svi.installer.ID)
		return nil
	case <-time.After(svi.timeLimit):
		checker.stop()
		return fmt.Errorf("Timeout reached before installation of '%s' finished", svi.installer.ID)
	case <-diedC:
		checker.stop()
		return fmt.Errorf("Installation of %s was interrupted", svi.installer.ID)
	}
}

func executeScript(installer Installer) (uint64, chan *process.DiedEvent, error) {
	diedC := make(chan *process.DiedEvent, 1)
	subscriber := func(event process.Event) {
		broadcastLogs(installer.ID, event)
		if event.Type() == process.DiedEventType {
			diedC <- event.(*process.DiedEvent)
		}
	}
	pb := process.NewBuilder()
	pb.CmdName(installer.ID)
	pb.CmdLine(installer.Script)
	pb.CmdType(installerCmdType)
	pb.SubscribeDefault(installer.ID, process.EventConsumerFunc(subscriber))
	p, err := pb.Start()
	if err != nil {
		return 0, nil, err
	}
	return p.Pid, diedC, nil
}

// dialChecker performs a servers availability check
type dialChecker struct {
	// period defines period between checks
	period time.Duration
	// stopped channel for interrupting servers checks
	stopped chan bool
}

func (cc *dialChecker) allAvailable(servers map[string]Server) chan bool {
	ticker := time.NewTicker(cc.period)
	state := make(chan bool, 1)
	go func() {
		for {
			ok := true
			select {
			case <-ticker.C:
				for _, server := range servers {
					if tryConn(server) != nil {
						ok = false
						break
					}
				}
				if ok {
					state <- ok
					ticker.Stop()
					return
				}
			case <-cc.stopped:
				close(state)
				ticker.Stop()
				return
			}
		}
	}()
	return state
}

// stop stops server health checking
func (cc *dialChecker) stop() {
	close(cc.stopped)
}

// tryConn tries to connect to specified server over tcp/udp
func tryConn(server Server) error {
	// port format should be '4411/tcp' or '4111'
	split := strings.Split(server.Port, "/")
	var protocol string
	if len(split) == 1 {
		protocol = "tcp"
	} else if len(split) == 2 {
		protocol = split[1]
	} else {
		return fmt.Errorf("Port format is not supported %s", server.Port)
	}
	port := split[0]
	addr := "localhost:" + port
	conn, err := net.Dial(protocol, addr)
	if err != nil {
		return fmt.Errorf("Failed establish connection to "+addr, err)
	}
	return conn.Close()
}
