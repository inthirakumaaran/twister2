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
package edu.iu.dsc.tws.comms.mpi.io;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import edu.iu.dsc.tws.common.config.Config;
import edu.iu.dsc.tws.comms.api.DataFlowOperation;
import edu.iu.dsc.tws.comms.api.MessageReceiver;

public class GatherFileBasedReceiver implements MessageReceiver {
  private static final Logger LOG = Logger.getLogger(GatherFileBasedReceiver.class.getName());
  // lets keep track of the messages
  // for each task we need to keep track of incoming messages
  private Map<Integer, Map<Integer, List<Object>>> messages = new HashMap<>();
  private Map<Integer, Map<Integer, Integer>> counts = new HashMap<>();

  private int count = 0;

  private long start = System.nanoTime();

  private int id;

  public GatherFileBasedReceiver(int id) {
    this.id = id;
  }

  @Override
  public void init(Config cfg, DataFlowOperation op, Map<Integer, List<Integer>> expectedIds) {
    for (Map.Entry<Integer, List<Integer>> e : expectedIds.entrySet()) {
      Map<Integer, List<Object>> messagesPerTask = new HashMap<>();
      Map<Integer, Integer> countsPerTask = new HashMap<>();

      for (int i : e.getValue()) {
        messagesPerTask.put(i, new ArrayList<Object>());
        countsPerTask.put(i, 0);
      }

      LOG.info(String.format("%d Final Task %d receives from %s",
          id, e.getKey(), e.getValue().toString()));

      messages.put(e.getKey(), messagesPerTask);
      counts.put(e.getKey(), countsPerTask);
    }
  }

  @Override
  public boolean onMessage(int source, int path, int target, int flags, Object object) {
    // add the object to the map
    boolean canAdd = true;
    if (count == 0) {
      start = System.nanoTime();
    }

    try {
      List<Object> m = messages.get(target).get(source);
      Integer c = counts.get(target).get(source);
      if (m.size() > 128) {
        canAdd = false;
      } else {
        m.add(object);
        counts.get(target).put(source, c + 1);
      }

      return canAdd;
    } catch (Throwable t) {

      t.printStackTrace();
    }
    return true;
  }

  public void progress() {
    for (int t : messages.keySet()) {
      boolean canProgress = true;
      while (canProgress) {
        // now check weather we have the messages for this source
        Map<Integer, List<Object>> map = messages.get(t);
        boolean found = true;
        Object o = null;
        for (Map.Entry<Integer, List<Object>> e : map.entrySet()) {
          if (e.getValue().size() == 0) {
            found = false;
            canProgress = false;
          } else {
            o = e.getValue().get(0);
          }
        }
        if (found) {
          for (Map.Entry<Integer, List<Object>> e : map.entrySet()) {
            o = e.getValue().remove(0);
          }
          if (o != null) {
            count++;
            if (count % 1000 == 0) {
              LOG.info(String.format("%d Last %d count: %d %s",
                  id, t, count, counts));
            }
            if (count >= 10000) {
              LOG.info("Total time: " + (System.nanoTime() - start) / 1000000
                  + " Count: " + count);
            }
          } else {
            LOG.severe("We cannot find an object and this is not correct");
          }
        }
      }
    }
  }
}
