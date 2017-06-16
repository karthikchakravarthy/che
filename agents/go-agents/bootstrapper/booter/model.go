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

// Installer represents configuration of entity that might be installed into machine and brings functionality
type Installer struct {
	// ID the identifier of the installer
	ID string `json:"id"`

	// Description the description of the installer
	Description string `json:"description"`

	// Version the version of the installer
	Version string `json:"version"`

	// Script the script to be applied when machine is started
	Script string `json:"script"`

	// Servers the mapping of server ref to server configuration
	Servers map[string]Server `json:"servers"`
}

func (installer Installer) HasServers() bool { return len(installer.Servers) != 0 }

// Server represents set of configuration that can be
type Server struct {
	// Port the server port used
	Port string `json:"port"`

	// Protocol the protocol for configuring preview url of the server
	Protocol string `json:"protocol"`

	// Path used by server
	Path string `json:"path"`
}
