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

package main

import (
	"log"
	"os"

	"github.com/eclipse/che/agents/go-agents/bootstrapper/booter"
	"github.com/eclipse/che/agents/go-agents/bootstrapper/cfg"
	"github.com/eclipse/che/agents/go-agents/core/jsonrpc"
	"github.com/eclipse/che/agents/go-agents/core/jsonrpc/jsonrpcws"
)

func main() {
	log.SetOutput(os.Stdout)

	cfg.Parse()
	cfg.Print()

	booter.Init(
		cfg.RuntimeID,
		cfg.MachineName,
		cfg.InstallerTimeoutSec,
		cfg.CheckServersPeriodSec,
	)
	booter.AddAll(cfg.ReadInstallersConfig())

	var statusesEndpoint *jsonrpc.Tunnel = connect(cfg.PushStatusesEndpoint)
	var logsEndpoint *jsonrpc.Tunnel
	if cfg.PushStatusesEndpoint == cfg.PushLogsEndpoint {
		logsEndpoint = statusesEndpoint
	} else {
		logsEndpoint = connect(cfg.PushLogsEndpoint)
	}

	booter.PushStatuses(statusesEndpoint)
	booter.PushLogs(logsEndpoint)

	if err := booter.Start(); err != nil {
		log.Fatal(err)
	}
}

func connect(endpoint string) *jsonrpc.Tunnel {
	conn, err := jsonrpcws.Dial(endpoint)
	if err != nil {
		log.Fatalf("Couldn't connect to endpoint '%s', due to error '%s'", cfg.PushStatusesEndpoint, err)
	}
	tunnel := jsonrpc.NewManagedTunnel(conn)
	tunnel.Go()
	return tunnel
}
