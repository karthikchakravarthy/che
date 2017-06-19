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

package cfg

import (
	"encoding/json"
	"flag"
	"io/ioutil"
	"log"
	"os"
	"strings"

	"github.com/eclipse/che/agents/go-agents/bootstrapper/booter"
)

var (
	// FilePath path to config file.
	FilePath string

	// PushStatusesEndpoint where to push statuses.
	PushStatusesEndpoint string

	// PushLogsEndpoint where to push logs.
	PushLogsEndpoint string

	// RuntimeID the id of workspace runtime this machine belongs to.
	RuntimeID    booter.RuntimeID
	runtimeIDRaw string

	// MachineName is the name of this machine.
	MachineName string

	// InstallerTimeoutSec how much time(seconds) is given for one installation to complete.
	InstallerTimeoutSec int

	// CheckServersPeriodSec how much time(seconds) is between servers checks for one installer.
	CheckServersPeriodSec int
)

func init() {
	curDir, err := os.Getwd()
	if err != nil {
		log.Fatal(err)
	}
	flag.StringVar(
		&FilePath,
		"file",
		curDir+string(os.PathSeparator)+"config.json",
		"Path to configuration file on filesystem",
	)
	flag.StringVar(
		&PushStatusesEndpoint,
		"push-endpoint",
		"",
		"WebSocket endpoint where to push statuses",
	)
	flag.StringVar(
		&PushLogsEndpoint,
		"push-logs-endpoint",
		"",
		"WebSocket endpoint where to push logs",
	)
	flag.StringVar(
		&runtimeIDRaw,
		"runtime-id",
		"",
		"The identifier of the runtime in format 'workspace:environment:owner'",
	)
	flag.StringVar(
		&MachineName,
		"machine-name",
		"",
		"The name of the machine in which this bootstrapper is running",
	)
	flag.IntVar(
		&InstallerTimeoutSec,
		"installer-timeout",
		120, // 2m
		`Time(in seconds) given for one installer to complete its installation.
	If installation is not finished in time it will be interrupted`,
	)
	flag.IntVar(
		&CheckServersPeriodSec,
		"server-check-period",
		3, // 2m
		`Time(in seconds) between servers availability checks.
	Once servers for one installer available - checks stopped`,
	)
}

func Parse() {
	flag.Parse()

	// push-endpoint
	if len(PushStatusesEndpoint) == 0 {
		log.Fatal("Push endpoint required(set it with -push-endpoint argument)")
	}
	if !strings.HasPrefix(PushStatusesEndpoint, "ws") {
		log.Fatal("Push endpoint protocol must be either ws or wss")
	}

	// push-logs-endpoint
	if len(PushLogsEndpoint) != 0 && !strings.HasPrefix(PushLogsEndpoint, "ws") {
		log.Fatal("Push logs endpoint protocol must be either ws or wss")
	}

	// runtime-id
	if len(runtimeIDRaw) == 0 {
		log.Fatal("Runtime ID required(set it with -runtime-id argument)")
	}
	parts := strings.Split(runtimeIDRaw, ":")
	if len(parts) != 3 {
		log.Fatalf("Expected runtime id to be in format 'workspace:env:owner'")
	}
	RuntimeID = booter.RuntimeID{Workspace: parts[0], Environment: parts[1], Owner: parts[2]}

	// machine-name
	if len(MachineName) == 0 {
		log.Fatal("Machine name required(set it with -machine-name argument)")
	}

	if InstallerTimeoutSec <= 0 {
		log.Fatal("Installer timeout must be > 0")
	}
	if CheckServersPeriodSec <= 0 {
		log.Fatal("Servers check period must be > 0")
	}
}

// Print prints configuration.
func Print() {
	log.Print("Bootstrapper configuration")
	log.Printf("  Push endpoint: %s", PushStatusesEndpoint)
	log.Printf("  Push logs Endpoint: %s", PushLogsEndpoint)
	log.Print("  Runtime ID:")
	log.Printf("    Workspace: %s", RuntimeID.Workspace)
	log.Printf("    Environment: %s", RuntimeID.Environment)
	log.Printf("    Owner: %s", RuntimeID.Owner)
	log.Printf("  Machine name: %s", MachineName)
	log.Printf("  Installer timeout: %dseconds", InstallerTimeoutSec)
	log.Printf("  Check servers period: %dseconds", CheckServersPeriodSec)
}

// ReadInstallersConfig reads content of file by path cfg.FilePath,
// parses its content as array of installers and returns it.
// If any error occurs during read, log.Fatal is called.
func ReadInstallersConfig() []booter.Installer {
	f, err := os.Open(FilePath)
	if err != nil {
		log.Fatal(err)
	}

	defer func() {
		if err := f.Close(); err != nil {
			log.Printf("Can't close installers config source, cause: %s", err)
		}
	}()

	raw, err := ioutil.ReadAll(f)
	if err != nil {
		log.Fatal(err)
	}

	installers := make([]booter.Installer, 0)
	if err := json.Unmarshal(raw, &installers); err != nil {
		log.Fatal(err)
	}
	return installers
}
