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
import java.util.logging.Logger;

import org.apache.commons.lang3.tuple.ImmutablePair;
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
import edu.iu.dsc.tws.comms.routing.BinaryTreeRouter;
import edu.iu.dsc.tws.comms.utils.KryoSerializer;

public class MPIDataFlowBroadcast implements DataFlowOperation, MPIMessageReceiver {
  private static final Logger LOG = Logger.getLogger(MPIDataFlowBroadcast.class.getName());

  private int source;

  private Set<Integer> destinations;

  private BinaryTreeRouter router;

  private MessageReceiver finalReceiver;

  private MPIDataFlowOperation delegete;
  private Config config;
  private TaskPlan instancePlan;
  private int executor;
  private int edge;
  private MessageType type;
  private Map<Integer, ArrayBlockingQueue<Pair<Object, MPISendMessage>>>
      pendingSendMessagesPerSource = new HashMap<>();

  public MPIDataFlowBroadcast(TWSMPIChannel channel, int src, Set<Integer> dests,
                              MessageReceiver finalRcvr) {
    this.source = src;
    this.destinations = dests;
    this.finalReceiver = finalRcvr;

    this.delegete = new MPIDataFlowOperation(channel);
  }

  @Override
  public void close() {
  }

  @Override
  public void finish() {

  }

  @Override
  public MessageType getType() {
    return type;
  }

  @Override
  public TaskPlan getTaskPlan() {
    return instancePlan;
  }

  public boolean receiveMessage(MPIMessage currentMessage, Object object) {
    MessageHeader header = currentMessage.getHeader();

    // we always receive to the main task
//    LOG.info(String.format("%d received message from %d", instancePlan.getThisExecutor(),
//        currentMessage.getHeader().getSourceId()));
    // check weather this message is for a sub task

//      LOG.info(String.format("%d calling fina receiver", instancePlan.getThisExecutor()));
    return finalReceiver.onMessage(
        header.getSourceId(), MPIContext.DEFAULT_PATH,
        router.mainTaskOfExecutor(instancePlan.getThisExecutor(),
            MPIContext.DEFAULT_PATH), header.getFlags(), object);
  }

  @Override
  public void init(Config cfg, MessageType t, TaskPlan tPlan, int ed) {
    this.config = cfg;
    this.instancePlan = tPlan;
    this.type = t;
    // we will only have one distinct route
    router = new BinaryTreeRouter(cfg, tPlan, source, destinations);

    if (this.finalReceiver != null) {
      this.finalReceiver.init(cfg, this, receiveExpectedTaskIds());
    } else {
      throw new RuntimeException("Final receiver is required");
    }

    LOG.info(String.format("%d all send tasks: %s", executor, router.sendQueueIds()));


    Map<Integer, Queue<Pair<Object, MPIMessage>>> pendingReceiveMessagesPerSource = new HashMap<>();
    Map<Integer, Queue<MPIMessage>> pendingReceiveDeSerializations = new HashMap<>();

    Set<Integer> srcs = router.sendQueueIds();
    for (int s : srcs) {
      // later look at how not to allocate pairs for this each time
      ArrayBlockingQueue<Pair<Object, MPISendMessage>> pendingSendMessages =
          new ArrayBlockingQueue<Pair<Object, MPISendMessage>>(
              MPIContext.sendPendingMax(cfg));
      pendingSendMessagesPerSource.put(s, pendingSendMessages);
    }

    int maxReceiveBuffers = MPIContext.receiveBufferCount(cfg);
    int receiveExecutorsSize = receivingExecutors().size();
    if (receiveExecutorsSize == 0) {
      receiveExecutorsSize = 1;
    }
    Set<Integer> execs = router.receivingExecutors();
    for (int e : execs) {
      int capacity = maxReceiveBuffers * 2 * receiveExecutorsSize;
      Queue<Pair<Object, MPIMessage>> pendingReceiveMessages =
          new ArrayBlockingQueue<Pair<Object, MPIMessage>>(
              capacity);
      pendingReceiveMessagesPerSource.put(e, pendingReceiveMessages);
      pendingReceiveDeSerializations.put(e, new ArrayBlockingQueue<MPIMessage>(capacity));
    }

    KryoSerializer kryoSerializer = new KryoSerializer();
    kryoSerializer.init(new HashMap<String, Object>());

    MessageDeSerializer messageDeSerializer = new MPIMessageDeSerializer(kryoSerializer);
    MessageSerializer messageSerializer = new MPIMessageSerializer(kryoSerializer);

    delegete.init(cfg, t, tPlan, ed,
        router.receivingExecutors(), router.isLastReceiver(), this,
        pendingSendMessagesPerSource, pendingReceiveMessagesPerSource,
        pendingReceiveDeSerializations, messageSerializer, messageDeSerializer);
  }

  @Override
  public boolean sendPartial(int src, Object message, int flags) {
    throw new RuntimeException("Not supported method");
  }

  @Override
  public boolean send(int src, Object message, int flags) {
    return delegete.sendMessage(src, message, 0, flags, sendRoutingParameters(src, 0));
  }

  @Override
  public boolean send(int src, Object message, int flags, int path) {
    return delegete.sendMessage(src, message, path, flags, sendRoutingParameters(src, 0));
  }

  @Override
  public boolean sendPartial(int src, Object message, int flags, int path) {
    return false;
  }

  @Override
  public void progress() {
    delegete.progress();
    finalReceiver.progress();
  }

  public boolean passMessageDownstream(Object object, MPIMessage currentMessage) {
    int src = router.mainTaskOfExecutor(instancePlan.getThisExecutor(), MPIContext.DEFAULT_PATH);
    RoutingParameters routingParameters = sendRoutingParameters(src, MPIContext.DEFAULT_PATH);
    ArrayBlockingQueue<Pair<Object, MPISendMessage>> pendingSendMessages =
        pendingSendMessagesPerSource.get(src);

    MPIMessage mpiMessage = new MPIMessage(src, type, MPIMessageDirection.OUT, delegete);

    // create a send message to keep track of the serialization
    // at the intial stage the sub-edge is 0
    int di = -1;
    if (routingParameters.getExternalRoutes().size() > 0) {
      di = routingParameters.getDestinationId();
    }
    MPISendMessage sendMessage = new MPISendMessage(src, mpiMessage, edge,
        di, MPIContext.DEFAULT_PATH, currentMessage.getHeader().getFlags(),
        routingParameters.getInternalRoutes(),
        routingParameters.getExternalRoutes());

    // now try to put this into pending
    return pendingSendMessages.offer(
        new ImmutablePair<Object, MPISendMessage>(object, sendMessage));
  }

  public RoutingParameters sendRoutingParameters(int s, int path) {
    RoutingParameters routingParameters = new RoutingParameters();
    // get the expected routes
    Map<Integer, Set<Integer>> internalRouting = router.getInternalSendTasks(source);
    if (internalRouting == null) {
      throw new RuntimeException("Un-expected message from source: " + s);
    }

    Set<Integer> internalSourceRouting = internalRouting.get(s);
    if (internalSourceRouting != null) {
      // we always use path 0 because only one path
//      LOG.info(String.format("%d internal routing %s", executor, internalSourceRouting));
      routingParameters.addInternalRoutes(internalSourceRouting);
    } else {
      LOG.info(String.format("%d No internal routes for source %d", executor, s));
    }

    // get the expected routes
    Map<Integer, Set<Integer>> externalRouting = router.getExternalSendTasks(s);
    if (externalRouting == null) {
      throw new RuntimeException("Un-expected message from source: " + s);
    } /*else {
      LOG.info(String.format("%d No external routes for source %d", executor, s));
    }*/
    Set<Integer> externalSourceRouting = externalRouting.get(s);
    if (externalSourceRouting != null) {
//      LOG.info(String.format("%d external routing %s", executor, externalSourceRouting));
      // we always use path 0 because only one path
      routingParameters.addExternalRoutes(externalSourceRouting);
    }
    return routingParameters;
  }

  @Override
  public boolean receiveSendInternally(int src, int t, int path, int flags, Object message) {
    return finalReceiver.onMessage(src, path, t, flags, message);
  }

  protected Set<Integer> receivingExecutors() {
    return router.receivingExecutors();
  }

  public Map<Integer, List<Integer>> receiveExpectedTaskIds() {
    return router.receiveExpectedTaskIds();
  }

  protected boolean isLast(int src, int path, int taskIdentifier) {
    return false;
  }

  protected boolean isLastReceiver() {
    return true;
  }
}

