//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
package edu.iu.dsc.tws.comms.mpi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;

import org.apache.commons.lang3.tuple.Pair;

import edu.iu.dsc.tws.common.config.Config;
import edu.iu.dsc.tws.comms.api.DataFlowOperation;
import edu.iu.dsc.tws.comms.api.MessageHeader;
import edu.iu.dsc.tws.comms.api.MessageReceiver;
import edu.iu.dsc.tws.comms.api.MessageType;
import edu.iu.dsc.tws.comms.core.TaskPlan;
import edu.iu.dsc.tws.comms.mpi.io.MPIMessageDeSerializer;
import edu.iu.dsc.tws.comms.mpi.io.MPIMessageSerializer;
import edu.iu.dsc.tws.comms.mpi.io.MessageDeSerializer;
import edu.iu.dsc.tws.comms.mpi.io.MessageSerializer;
import edu.iu.dsc.tws.comms.routing.DirectRouter;
import edu.iu.dsc.tws.comms.utils.KryoSerializer;
import edu.iu.dsc.tws.comms.utils.TaskPlanUtils;

/**
 * A direct data flow operation sends peer to peer messages
 */
public class MPIDirectDataFlowCommunication implements DataFlowOperation, MPIMessageReceiver {
  private Set<Integer> sources;
  private int destination;
  private DirectRouter router;
  private MessageReceiver finalReceiver;
  private MPIDataFlowOperation delegete;
  private Config config;
  private TaskPlan instancePlan;
  private int executor;

  public MPIDirectDataFlowCommunication(TWSMPIChannel channel,
                                        Set<Integer> srcs, int dest,
                                        MessageReceiver finalRcvr) {
    this.sources = srcs;
    this.destination = dest;
    this.finalReceiver = finalRcvr;
  }

  @Override
  public boolean receiveMessage(MPIMessage currentMessage, Object object) {
    MessageHeader header = currentMessage.getHeader();
    // check weather this message is for a sub task
    return finalReceiver.onMessage(header.getSourceId(), 0, destination, header.getFlags(), object);
  }

  @Override
  public boolean receiveSendInternally(int source, int t, int path, int flags, Object message) {
    // we only have one destination in this case
    if (t != destination) {
      throw new RuntimeException("We only have one destination");
    }

    // okay this must be for the
    return finalReceiver.onMessage(source, path, t, flags, message);
  }

  @Override
  public boolean passMessageDownstream(Object object, MPIMessage currentMessage) {
    return false;
  }

  protected Map<Integer, List<Integer>> receiveExpectedTaskIds() {
    return this.router.receiveExpectedTaskIds();
  }

  @Override
  public void init(Config cfg, MessageType t, TaskPlan taskPlan, int edge) {
    this.router = new DirectRouter(taskPlan, sources, destination);

    if (this.finalReceiver != null && isLastReceiver()) {
      this.finalReceiver.init(cfg, this, receiveExpectedTaskIds());
    }

    Map<Integer, ArrayBlockingQueue<Pair<Object, MPISendMessage>>> pendingSendMessagesPerSource =
        new HashMap<>();
    Map<Integer, Queue<Pair<Object, MPIMessage>>> pendingReceiveMessagesPerSource = new HashMap<>();
    Map<Integer, Queue<MPIMessage>> pendingReceiveDeSerializations = new HashMap<>();

    Set<Integer> srcs = TaskPlanUtils.getTasksOfThisExecutor(taskPlan, sources);
    for (int s : srcs) {
      // later look at how not to allocate pairs for this each time
      ArrayBlockingQueue<Pair<Object, MPISendMessage>> pendingSendMessages =
          new ArrayBlockingQueue<Pair<Object, MPISendMessage>>(
              MPIContext.sendPendingMax(cfg));
      pendingSendMessagesPerSource.put(s, pendingSendMessages);
      pendingReceiveDeSerializations.put(s, new ArrayBlockingQueue<MPIMessage>(
          MPIContext.sendPendingMax(cfg)));
    }

    KryoSerializer kryoSerializer = new KryoSerializer();
    kryoSerializer.init(new HashMap<String, Object>());

    MessageDeSerializer messageDeSerializer = new MPIMessageDeSerializer(kryoSerializer);
    MessageSerializer messageSerializer = new MPIMessageSerializer(kryoSerializer);

    delegete.init(cfg, t, taskPlan, edge, router.receivingExecutors(),
        isLastReceiver(), this, pendingSendMessagesPerSource, pendingReceiveMessagesPerSource,
        pendingReceiveDeSerializations, messageSerializer, messageDeSerializer);
  }

  @Override
  public boolean sendPartial(int source, Object message, int flags) {
    throw new RuntimeException("This method is not used by direct communication");
  }

  @Override
  public boolean send(int source, Object message, int flags) {
    return delegete.sendMessage(source, message, 0, flags, sendRoutingParameters(source, 0));
  }

  @Override
  public boolean send(int source, Object message, int flags, int path) {
    return delegete.sendMessage(source, message, path, flags, sendRoutingParameters(source, path));
  }

  @Override
  public boolean sendPartial(int source, Object message, int flags, int path) {
    return false;
  }

  @Override
  public void progress() {
    delegete.progress();
    finalReceiver.progress();
  }

  @Override
  public void close() {
  }

  @Override
  public void finish() {
  }

  @Override
  public MessageType getType() {
    return null;
  }

  @Override
  public TaskPlan getTaskPlan() {
    return null;
  }

  private boolean isLastReceiver() {
    return router.isLastReceiver();
  }

  private RoutingParameters sendRoutingParameters(int source, int path) {
    RoutingParameters routingParameters = new RoutingParameters();
    // get the expected routes
    Map<Integer, Set<Integer>> internalRoutes = router.getInternalSendTasks(source);
    if (internalRoutes == null) {
      throw new RuntimeException("Un-expected message from source: " + source);
    }

    Set<Integer> internalSourceRouting = internalRoutes.get(source);
    if (internalSourceRouting != null) {
      // we always use path 0 because only one path
      routingParameters.addInternalRoutes(internalSourceRouting);
    }

    // get the expected routes
    Map<Integer, Set<Integer>> externalRouting = router.getExternalSendTasks(source);
    if (externalRouting == null) {
      throw new RuntimeException("Un-expected message from source: " + source);
    }

    Set<Integer> externalSourceRouting = externalRouting.get(source);
    if (externalSourceRouting != null) {
      // we always use path 0 because only one path
      routingParameters.addExternalRoutes(externalSourceRouting);
    }
    routingParameters.setDestinationId(destination);
    return routingParameters;
  }
}
