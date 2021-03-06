// Copyright 2016 Twitter. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package edu.iu.dsc.tws.rsched.uploaders.scp;

import java.util.logging.Logger;

import edu.iu.dsc.tws.rsched.utils.ProcessUtils;

public class ScpController {
  private static final Logger LOG = Logger.getLogger(ScpController.class.getName());
  private String scpOptions;
  private String scpConnection;
  private String sshOptions;
  private String sshConnection;

  public ScpController(String scpOptions, String scpConnection, String sshOptions,
                       String sshConnection) {
    this.scpOptions = scpOptions;
    this.scpConnection = scpConnection;

    this.sshOptions = sshOptions;
    this.sshConnection = sshConnection;

  }

  public boolean mkdirsIfNotExists(String dir) {
    // an example ssh command created by the format looks like this:
    // ssh -i ~/.ssh/id_rsa -p 23 user@example.com mkdir -p /twister2/repository/...
    String command = String.format("ssh %s %s mkdir -p %s", sshOptions, sshConnection, dir);
    return 0 == ProcessUtils.runProcess(command, null);
  }

  public boolean copyFromLocalFile(String source, String destination) {
    // an example scp command created by the format looks like this:
    // scp -i ~/.ssh/id_rsa -p 23 ./foo.tar.gz user@example.com:/twister2/foo.tar.gz
    String command =
        String.format("scp %s %s %s:%s", scpOptions, source, scpConnection, destination);
    return 0 == ProcessUtils.runProcess(command, null);
  }

  public boolean delete(String filePath) {
    String command = String.format("ssh %s %s rm -rf %s", sshOptions, sshConnection, filePath);
    return 0 == ProcessUtils.runProcess(command, null);
  }
}
