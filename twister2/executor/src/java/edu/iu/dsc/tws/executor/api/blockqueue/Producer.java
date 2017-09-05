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
package edu.iu.dsc.tws.executor.api.blockqueue;

import java.util.concurrent.BlockingQueue;

/**
 * Created by vibhatha on 9/5/17.
 */
public class Producer implements Runnable {
  private final BlockingQueue queue;
  Producer(BlockingQueue q) { queue = q; }
  public void run() {
    try {
      int i=0;
      while (i<=5) { queue.put(produce(i));
      i++;}
    } catch (InterruptedException ex) {

    }
  }
  public Object produce(int i) {

    System.out.println("Producing  "+i);
    return new String("Hello : "+i);
  }
}