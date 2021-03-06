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
package edu.iu.dsc.tws.examples.basic.streaming.wordcount;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import edu.iu.dsc.tws.common.config.Config;
import edu.iu.dsc.tws.comms.api.DataFlowOperation;
import edu.iu.dsc.tws.comms.api.KeyedMessageReceiver;
import edu.iu.dsc.tws.comms.mpi.io.MultiObject;

public class WordAggregate implements KeyedMessageReceiver {
  private static final Logger LOG = Logger.getLogger(WordAggregate.class.getName());

  private Config config;

  private DataFlowOperation operation;

  private Map<String, Integer> wordCounts = new HashMap<>();

  @Override
  public void init(Config cfg, DataFlowOperation op,
                   Map<Integer, Map<Integer, List<Integer>>> expectedIds) {

  }

  @Override
  public boolean onMessage(int source, int path, int target, int flags, Object object) {
    if (object instanceof List) {
      for (Object o : (List) object) {
        LOG.info("Object: " + o);
        if (o instanceof MultiObject) {
          addValue(((MultiObject) o).getObject().toString());
        }
      }
    } else if (object instanceof MultiObject) {
      String value = ((MultiObject) object).getObject().toString();
      addValue(value);
    } else {
      addValue(object.toString());
    }


    return true;
  }

  private void addValue(String value) {
    int count = 0;
    if (wordCounts.containsKey(value)) {
      count = wordCounts.get(value);
    }
    count++;
    wordCounts.put(value, count);
    LOG.info("Words: " + wordCounts);
  }

  @Override
  public void progress() {
    // nothing to do here
  }
}
