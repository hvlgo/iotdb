/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.iotdb.db.queryengine.plan.relational.planner.node;

import org.apache.iotdb.db.queryengine.plan.planner.plan.node.PlanNode;
import org.apache.iotdb.db.queryengine.plan.planner.plan.node.PlanNodeId;
import org.apache.iotdb.db.queryengine.plan.planner.plan.node.PlanVisitor;
import org.apache.iotdb.db.queryengine.plan.planner.plan.node.process.SingleChildProcessNode;
import org.apache.iotdb.db.queryengine.plan.relational.planner.OrderingScheme;
import org.apache.iotdb.db.queryengine.plan.relational.planner.Symbol;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public class SortNode extends SingleChildProcessNode {
  private final OrderingScheme orderingScheme;
  private final boolean partial;

  public SortNode(PlanNodeId id, PlanNode child, OrderingScheme scheme, boolean partial) {
    super(id, child);
    this.orderingScheme = scheme;
    this.partial = partial;
  }

  @Override
  public PlanNode clone() {
    return new SortNode(id, child, orderingScheme, partial);
  }

  @Override
  public List<String> getOutputColumnNames() {
    throw new UnsupportedOperationException();
  }

  @Override
  public <R, C> R accept(PlanVisitor<R, C> visitor, C context) {
    return visitor.visitSort(this, context);
  }

  @Override
  protected void serializeAttributes(ByteBuffer byteBuffer) {}

  @Override
  protected void serializeAttributes(DataOutputStream stream) throws IOException {}

  @Override
  public List<Symbol> getOutputSymbols() {
    return child.getOutputSymbols();
  }

  public OrderingScheme getOrderingScheme() {
    return orderingScheme;
  }
}
