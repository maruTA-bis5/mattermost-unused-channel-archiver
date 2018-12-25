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

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import lombok.RequiredArgsConstructor;
import net.bis5.mattermost.client4.ApiResponse;
import net.bis5.mattermost.client4.MattermostClient;
import net.bis5.mattermost.client4.Pager;
import net.bis5.mattermost.model.Channel;
import net.bis5.mattermost.model.ChannelList;
import net.bis5.mattermost.model.PostList;
import net.bis5.mattermost.model.PostType;
import net.bis5.mattermost.model.Team;

@RequiredArgsConstructor
class UnusedChannelArchiver {

  private final Parameters parameters;

  void execute() throws Exception {
    // create client
    MattermostClient client = createClient();
    // try login
    if (!canLogin(client)) {
      // TODO show error message
      return;
    }
    try (MattermostClient _client = client) { // Java8 ugry hack
      // verify team access priviledge
      Optional<Team> targetTeam = getTargetTeam(client);
      // fetch all public channels
      targetTeam.map(t -> fetchAllPublicChannels(client, t))
          // extract archive target channels
          .map(cl -> extractUnusedChannels(client, cl))
          // if dryRun=true, print archive target channels and exit program
          .map(cl -> clearWhenDryRun(cl))
          // archive each target chahnel
          .map(cl -> archiveEachChannel(client, cl))
          // report archive results
          .ifPresent(results -> reportArchiveResults(results));

      // XXX finallyに書きたかったがclient#closeがJerseyClientを既にクローズしているので死ぬ
      // client#closeでログアウトするべきでは?
      client.logout();
    }
  }

  private void reportArchiveResults(List<Pair<Channel, Boolean>> results) {
    results.forEach(p -> System.out.printf("Channel: ~%s(%s), Result: %s%n", p.getLeft().getName(),
        p.getLeft().getDisplayName(), booleanToOkNg(p.getRight())));
  }

  private String booleanToOkNg(Boolean value) {
    return value ? "OK" : "NG";
  }

  private List<Pair<Channel, Boolean>> archiveEachChannel(MattermostClient client,
      Collection<Channel> channels) {
    return channels.stream() //
        .map(ch -> archiveChannel(client, ch)) //
        .collect(Collectors.toList());
  }

  private Pair<Channel, Boolean> archiveChannel(MattermostClient client, Channel channel) {
    ApiResponse<Boolean> result = client.deleteChannel(channel.getId());
    return Pair.of(channel, result.readEntity());
  }

  private Collection<Channel> clearWhenDryRun(Collection<Channel> channels) {
    if (parameters.isDryRun()) {
      // log
      System.out.println("Dry Run mode");
      List<Pair<Channel, Boolean>> reportResults =
          channels.stream().map(ch -> Pair.of(ch, true)).collect(Collectors.toList());
      reportArchiveResults(reportResults);
      return null;
    } else {
      return channels;
    }
  }

  private Collection<Channel> extractUnusedChannels(MattermostClient client,
      Collection<Channel> channels) {
    ZonedDateTime oneYearAgo = ZonedDateTime.now().minusYears(1);
    List<Channel> unusedChannels = channels.stream() //
        .peek(System.out::println) // XXX debug
        // 作ってから1年たっていないチャンネルはアーカイブ対象外にする
        .filter(ch -> ch.getCreateAt() < oneYearAgo.toInstant().toEpochMilli()) //
        .filter(ch -> !hasUserPostSince(client, ch, oneYearAgo)).collect(Collectors.toList());

    return unusedChannels;
  }

  private boolean hasUserPostSince(MattermostClient client, Channel ch,
      ZonedDateTime targetDateTime) {
    // TODO ちゃんと全部降ってくるか確認する(60超えても大丈夫か?)
    PostList postList =
        client.getPostsSince(ch.getId(), targetDateTime.toInstant().toEpochMilli()).readEntity();

    return postList.getPosts().values().stream() //
        .filter(p -> p.getType().equals(PostType.DEFAULT)) //
        .peek(p -> System.out.printf("Found post in ~%s: %s%n", ch.getName(), p)) //
        .findAny() //
        .isPresent();
  }

  private Collection<Channel> fetchAllPublicChannels(MattermostClient client, Team team) {
    List<Channel> publicChannels = new ArrayList<>();
    Pager pager = Pager.defaultPager();
    publicChannels.addAll(fetchAllPublicChannels(client, team, pager));
    return publicChannels;
  }

  private Collection<? extends Channel> fetchAllPublicChannels(MattermostClient client, Team team,
      Pager pager) {
    List<Channel> publicChannels = new ArrayList<>();
    ApiResponse<ChannelList> channelsResponse =
        client.getPublicChannelsForTeam(team.getId(), pager);
    if (!channelsResponse.hasError()) {
      ChannelList channels = channelsResponse.readEntity();
      publicChannels.addAll(channels);
      if (channels.size() == pager.getPerPage()) {
        publicChannels.addAll(fetchAllPublicChannels(client, team, pager.nextPage()));
      }
    } else {
      // TODO log
    }
    return publicChannels;
  }

  private Optional<Team> getTargetTeam(MattermostClient client) {
    ApiResponse<Team> teamResponse = client.getTeamByName(parameters.getTargetTeamName());
    if (teamResponse.hasError()) {
      // TODO log
      return Optional.empty();
    } else {
      return Optional.of(teamResponse.readEntity());
    }
  }

  private MattermostClient createClient() {
    MattermostClient client = new MattermostClient(parameters.getServer());
    // MattermostClient client = new MattermostClient(parameters.getServer(),
    // Level.INFO);
    return client;
  }

  private boolean canLogin(MattermostClient client) {
    return null != client.login(parameters.getUserName(), parameters.getPassword());
  }
}
