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
package edu.iu.dsc.tws.comms.routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.iu.dsc.tws.comms.core.TaskPlan;

public class BinaryTree {
  private static final Logger LOG = Logger.getLogger(BinaryTree.class.getName());

  private int interNodeDegree;
  private int intraNodeDegree;
  private TaskPlan taskPlan;
  private int root;
  private Set<Integer> nodes;

  public BinaryTree(int interNodeDegree, int intraNodeDegree, TaskPlan taskPlan,
                    int source, Set<Integer> destinations) {
    this.interNodeDegree = interNodeDegree;
    this.intraNodeDegree = intraNodeDegree;
    this.taskPlan = taskPlan;
    this.root = source;
    this.nodes = destinations;
  }

  public static Node search(Node root, int taskId) {
    Queue<Node> queue = new LinkedList<>();
    queue.add(root);

    while (queue.size() > 0) {
      Node current = queue.poll();
      if (taskId >= 0 && current.getTaskId() == taskId) {
        return current;
      } else {
        queue.addAll(current.getChildren());
      }
    }

    return null;
  }

  public Node buildInterGroupTree(int index) {
    // get the groups hosting the component
    // rotate according to index, this will create a unique tree for each index
    List<Integer> groups = rotateList(new ArrayList<>(getGroupsHostingTasks(nodes)), index);
    LOG.log(Level.INFO, "Number of groups: " + groups.size());
    if (groups.size() == 0) {
      LOG.log(Level.WARNING, "Groups for destinations is zero");
      return null;
    }

    // sort the list
    Collections.sort(groups);
    Node rootNode = buildIntraGroupTree(getGroupHostingTask(this.root), index);
    if (rootNode == null) {
      LOG.log(Level.WARNING, "Intranode tree didn't built: " + groups.get(0));
      return null;
    }

    Queue<Node> queue = new LinkedList<>();
    Node current = rootNode;

    int i = 0;
    int currentInterNodeDegree = current.getChildren().size() + interNodeDegree;
    while (i < groups.size()) {
      if (current.getChildren().size() < currentInterNodeDegree) {
        Node e = buildIntraGroupTree(groups.get(i), index);
        if (e != null) {
          current.addChild(e);
          e.setParent(current);
          queue.add(e);
        } else {
          throw new RuntimeException("Stream manager with 0 components for building tree");
        }
        i++;
      } else {
        current = queue.poll();
        currentInterNodeDegree = current.getChildren().size() + interNodeDegree;
      }
    }
    return rootNode;
  }

  private Node buildIntraGroupTree(int groupId, int index) {
    // rotate according to index, this will create a unique tree for each index
    List<Integer> executorIds = rotateList(
        new ArrayList<>(taskPlan.getExecutesOfGroup(groupId)), index);
    if (executorIds.size() == 0) {
      return null;
    }
    LOG.log(Level.FINE, "Number of executors: " + executorIds.size());

    // sort the taskIds to make sure everybody creating the same tree
    Collections.sort(executorIds);

    // create the root of the tree
    Node rootNode = createTreeeNode(groupId, executorIds.get(0), index);

    // now lets create the tree
    Queue<Node> queue = new LinkedList<>();
    Node current = rootNode;
    int i = 0;
    while (i < executorIds.size()) {
      if (current.getChildren().size() < intraNodeDegree) {
        // create a tree node and add it to the current node as a child
        Node e = createTreeeNode(groupId, executorIds.get(i), index);
        current.addChild(e);
        e.setParent(current);
        queue.add(e);
        i++;
      } else {
        // the current node is filled, lets move to the next
        current = queue.poll();
      }
    }

    return rootNode;
  }

  private Node createTreeeNode(int groupId, int executorId, int rotateIndex) {
    List<Integer> channelsOfExecutorList = new ArrayList<>(
        taskPlan.getChannelsOfExecutor(executorId));
    Collections.sort(channelsOfExecutorList);
    // we will rotate according to rotate index
    channelsOfExecutorList = rotateList(channelsOfExecutorList, rotateIndex);

    int firstTaskId = channelsOfExecutorList.get(0);
    // this task act as the tree node
    Node node = new Node(firstTaskId, groupId);
    // add all the other tasks as direct children
    for (int i = 1; i < channelsOfExecutorList.size(); i++) {
      node.addDirectChild(channelsOfExecutorList.get(i));
    }
    return node;
  }

  private int getGroupHostingTask(int task) {
    int executor = taskPlan.getExecutorForChannel(task);
    return taskPlan.getGroupOfExecutor(executor);
  }

  private Set<Integer> getGroupsHostingTasks(Set<Integer> tasks) {
    Set<Integer> groups = new HashSet<>();
    for (int t : tasks) {
      int executor = taskPlan.getExecutorForChannel(t);
      int group = taskPlan.getGroupOfExecutor(executor);
      groups.add(group);
    }
    return groups;
  }

  private List<Integer> rotateList(List<Integer> original, int index) {
    List<Integer> rotate = new ArrayList<>();
    for (int i = 0; i < original.size(); i++) {
      rotate.add(original.get((i + index) / original.size()));
    }
    return rotate;
  }
}