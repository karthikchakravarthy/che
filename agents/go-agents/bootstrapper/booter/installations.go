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
	"net"
	"strings"
	"time"

	"github.com/eclipse/che/agents/go-agents/core/process"
)

// Process of getting certain installer software/servers installed.
type installation interface {

	// Check installation preconditions e.g. ports are free.
	// If error is returned installation is not executed.
	preCheck() error

	// Executes the installation in implementation specific way.
	execute() error
}

// Script installation executes script defined by installer.
// Installation is considered successful only if script exit code is 0.
type scriptInst struct {
	installer Installer
	timeout   time.Duration
}

func (sci *scriptInst) preCheck() error { return nil }

func (sci *scriptInst) execute() error {
	_, diedC, err := executeScript(sci.installer)
	if err != nil {
		fmt.Errorf("Scrip execution failed. Error: %s", err)
	}
	select {
	case died := <-diedC:
		if died.ExitCode != 0 {
			return fmt.Errorf(
				"Exit code for '%s' installation is '%d' while expected to be 0",
				sci.installer.ID,
				died.ExitCode,
			)
		}
	case <-time.After(sci.timeout):
		return fmt.Errorf("Timeout reached before installation of '%s' completed", sci.installer.ID)
	}
	return nil
}

// Server installation executes script defined by installer and
// wait all the defined servers to become available.
type serverInst struct {
	installer Installer
	period    time.Duration
	timeout   time.Duration
}

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
	case <-time.After(svi.timeout):
		checker.stop()
		return fmt.Errorf("Timeout reached before installation of '%s' finished", svi.installer.ID)
	case <-diedC:
		checker.stop()
		return fmt.Errorf("Process of installation '%s' exited before server became available", svi.installer.ID)
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
