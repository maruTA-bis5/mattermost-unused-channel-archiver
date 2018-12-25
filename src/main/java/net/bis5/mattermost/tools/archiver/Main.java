/*
 * Copyright (c) 2018-present, Takayuki Maruyama
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.bis5.mattermost.tools.archiver;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

public class Main {

  public static void main(String[] args) throws Exception {
    Options options = new Options();
    options.addOption("u", "username", true, "User name using login to Mattermost") //
        .addOption("p", "password", true, "Password using login to Mattermost") //
        .addOption("s", "server", true, "Mattermost URL(https://your-mattermost-host)") //
        .addOption("t", "teamname", true, "Target team name") //
        .addOption("d", "dryRun", false,
            "Display archive target channels and exit (don't run archive)");

    CommandLine commandLine = new DefaultParser().parse(options, args);
    Parameters params = Parameters.builder() //
        .userName(commandLine.getOptionValue("u")) //
        .password(commandLine.getOptionValue("p")) //
        .server(commandLine.getOptionValue("s")) //
        .dryRun(commandLine.hasOption("d")) //
        .targetTeamName(commandLine.getOptionValue("t")) //
        .build();
    if (!params.isValid()) {
      new HelpFormatter()
          .printHelp(String.format("%s -u username -p password -s server-url -t teamname [-d]",
              Main.class.getSimpleName()), options);
      System.exit(1);
    }
    new UnusedChannelArchiver(params).execute();
  }

}
