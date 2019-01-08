/*
 * Copyright 2016-2018 shardingsphere.io.
 * <p>
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
 * </p>
 */

package io.shardingsphere.core.optimizer;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import io.shardingsphere.api.algorithm.sharding.ListShardingValue;
import io.shardingsphere.core.optimizer.condition.ShardingConditions;
import io.shardingsphere.core.optimizer.insert.InsertOptimizeEngine;
import io.shardingsphere.core.optimizer.insert.InsertShardingCondition;
import io.shardingsphere.core.parsing.lexer.token.DefaultKeyword;
import io.shardingsphere.core.parsing.parser.context.condition.AndCondition;
import io.shardingsphere.core.parsing.parser.context.condition.Column;
import io.shardingsphere.core.parsing.parser.context.condition.Condition;
import io.shardingsphere.core.parsing.parser.context.insertvalue.InsertValue;
import io.shardingsphere.core.parsing.parser.context.table.Table;
import io.shardingsphere.core.parsing.parser.expression.SQLNumberExpression;
import io.shardingsphere.core.parsing.parser.expression.SQLPlaceholderExpression;
import io.shardingsphere.core.parsing.parser.sql.dml.insert.InsertStatement;
import io.shardingsphere.core.parsing.parser.token.InsertValuesToken;
import io.shardingsphere.core.parsing.parser.token.ItemsToken;
import io.shardingsphere.core.parsing.parser.token.TableToken;
import io.shardingsphere.core.routing.router.sharding.GeneratedKey;
import io.shardingsphere.core.rule.ShardingRule;
import io.shardingsphere.core.yaml.sharding.YamlShardingConfiguration;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

public final class InsertOptimizeEngineTest {
    
    private ShardingRule shardingRule;
    
    private InsertStatement insertStatementWithValuesWithPlaceHolder;
    
    private InsertStatement insertStatementWithValuesWithoutPlaceHolder;
    
    private InsertStatement insertStatementWithoutValuesWithPlaceHolder;
    
    private InsertStatement insertStatementWithoutValuesWithoutPlaceHolder;
    
    private List<Object> parameters;
    
    @Before
    public void setUp() throws IOException {
        URL url = InsertOptimizeEngineTest.class.getClassLoader().getResource("yaml/optimize-rule.yaml");
        Preconditions.checkNotNull(url, "Cannot found rewrite rule yaml configuration.");
        YamlShardingConfiguration yamlShardingConfig = YamlShardingConfiguration.unmarshal(new File(url.getFile()));
        shardingRule = yamlShardingConfig.getShardingRule(yamlShardingConfig.getDataSources().keySet());
        initializeWithValuesWithPlaceHolder();
        initializeInsertWithValuesWithoutPlaceHolder();
        parameters = new ArrayList<>(4);
        parameters.add(10);
        parameters.add("init");
        parameters.add(11);
        parameters.add("init");
    }
    
    private void initializeWithValuesWithPlaceHolder() {
        insertStatementWithValuesWithPlaceHolder = new InsertStatement();
        insertStatementWithValuesWithPlaceHolder.getTables().add(new Table("t_order", Optional.<String>absent()));
        insertStatementWithValuesWithPlaceHolder.setParametersIndex(4);
        insertStatementWithValuesWithPlaceHolder.setInsertValuesListLastPosition(45);
        insertStatementWithValuesWithPlaceHolder.addSQLToken(new TableToken(12, 0, "t_order"));
        insertStatementWithValuesWithPlaceHolder.addSQLToken(new InsertValuesToken(39, "t_order"));
        AndCondition andCondition1 = new AndCondition();
        andCondition1.getConditions().add(new Condition(new Column("user_id", "t_order"), new SQLPlaceholderExpression(0)));
        insertStatementWithValuesWithPlaceHolder.getConditions().getOrCondition().getAndConditions().add(andCondition1);
        AndCondition andCondition2 = new AndCondition();
        andCondition2.getConditions().add(new Condition(new Column("user_id", "t_order"), new SQLPlaceholderExpression(2)));
        insertStatementWithValuesWithPlaceHolder.getConditions().getOrCondition().getAndConditions().add(andCondition2);
        insertStatementWithValuesWithPlaceHolder.getInsertValues().getInsertValues().add(new InsertValue(DefaultKeyword.VALUES, "(?, ?)", 2));
        insertStatementWithValuesWithPlaceHolder.getInsertValues().getInsertValues().add(new InsertValue(DefaultKeyword.VALUES, "(?, ?)", 2));
    }
    
    private void initializeInsertWithValuesWithoutPlaceHolder() {
        insertStatementWithValuesWithoutPlaceHolder = new InsertStatement();
        insertStatementWithValuesWithoutPlaceHolder.getTables().add(new Table("t_order", Optional.<String>absent()));
        insertStatementWithValuesWithoutPlaceHolder.setParametersIndex(0);
        insertStatementWithValuesWithoutPlaceHolder.setInsertValuesListLastPosition(50);
        insertStatementWithValuesWithoutPlaceHolder.addSQLToken(new TableToken(12, 0, "t_order"));
        insertStatementWithValuesWithoutPlaceHolder.addSQLToken(new InsertValuesToken(42, "t_order"));
        ItemsToken itemsToken = new ItemsToken(34);
        itemsToken.getItems().add("order_id");
        insertStatementWithValuesWithoutPlaceHolder.addSQLToken(itemsToken);
        AndCondition andCondition = new AndCondition();
        andCondition.getConditions().add(new Condition(new Column("user_id", "t_order"), new SQLNumberExpression(12)));
        insertStatementWithValuesWithoutPlaceHolder.getConditions().getOrCondition().getAndConditions().add(andCondition);
        insertStatementWithValuesWithoutPlaceHolder.getInsertValues().getInsertValues().add(new InsertValue(DefaultKeyword.VALUES, "(12,'a')", 0));
    }
    
    @Test
    public void assertOptimizeWithValuesWithPlaceHolderWithGeneratedKey() {
        GeneratedKey generatedKey = new GeneratedKey(new Column("order_id", "t_order"));
        generatedKey.getGeneratedKeys().add(1);
        generatedKey.getGeneratedKeys().add(2);
        ShardingConditions actual = new InsertOptimizeEngine(shardingRule, insertStatementWithValuesWithPlaceHolder, parameters, generatedKey).optimize();
        assertFalse(actual.isAlwaysFalse());
        assertThat(actual.getShardingConditions().size(), is(2));
        assertThat(((InsertShardingCondition) actual.getShardingConditions().get(0)).getParameters().size(), is(3));
        assertThat(((InsertShardingCondition) actual.getShardingConditions().get(1)).getParameters().size(), is(3));
        assertThat(((InsertShardingCondition) actual.getShardingConditions().get(0)).getParameters().get(0), CoreMatchers.<Object>is(10));
        assertThat(((InsertShardingCondition) actual.getShardingConditions().get(0)).getParameters().get(1), CoreMatchers.<Object>is("init"));
        assertThat(((InsertShardingCondition) actual.getShardingConditions().get(0)).getParameters().get(2), CoreMatchers.<Object>is(1));
        assertThat(((InsertShardingCondition) actual.getShardingConditions().get(1)).getParameters().get(0), CoreMatchers.<Object>is(11));
        assertThat(((InsertShardingCondition) actual.getShardingConditions().get(1)).getParameters().get(1), CoreMatchers.<Object>is("init"));
        assertThat(((InsertShardingCondition) actual.getShardingConditions().get(1)).getParameters().get(2), CoreMatchers.<Object>is(2));
        assertThat(((InsertShardingCondition) actual.getShardingConditions().get(0)).getInsertValueExpression(), is("(?, ?, ?)"));
        assertThat(((InsertShardingCondition) actual.getShardingConditions().get(1)).getInsertValueExpression(), is("(?, ?, ?)"));
        assertThat(actual.getShardingConditions().get(0).getShardingValues().size(), is(2));
        assertThat(actual.getShardingConditions().get(1).getShardingValues().size(), is(2));
        assertShardingValue((ListShardingValue) actual.getShardingConditions().get(0).getShardingValues().get(0), 1);
        assertShardingValue((ListShardingValue) actual.getShardingConditions().get(0).getShardingValues().get(1), 10);
        assertShardingValue((ListShardingValue) actual.getShardingConditions().get(1).getShardingValues().get(0), 2);
        assertShardingValue((ListShardingValue) actual.getShardingConditions().get(1).getShardingValues().get(1), 11);
    }
    
    @Test
    public void assertOptimizeWithValuesWithPlaceHolderWithoutGeneratedKey() {
        insertStatementWithValuesWithPlaceHolder.setGenerateKeyColumnIndex(1);
        ShardingConditions actual = new InsertOptimizeEngine(shardingRule, insertStatementWithValuesWithPlaceHolder, parameters, null).optimize();
        assertFalse(actual.isAlwaysFalse());
        assertThat(actual.getShardingConditions().size(), is(2));
        assertThat(((InsertShardingCondition) actual.getShardingConditions().get(0)).getParameters().size(), is(2));
        assertThat(((InsertShardingCondition) actual.getShardingConditions().get(1)).getParameters().size(), is(2));
        assertThat(((InsertShardingCondition) actual.getShardingConditions().get(0)).getParameters().get(0), CoreMatchers.<Object>is(10));
        assertThat(((InsertShardingCondition) actual.getShardingConditions().get(0)).getParameters().get(1), CoreMatchers.<Object>is("init"));
        assertThat(((InsertShardingCondition) actual.getShardingConditions().get(1)).getParameters().get(0), CoreMatchers.<Object>is(11));
        assertThat(((InsertShardingCondition) actual.getShardingConditions().get(1)).getParameters().get(1), CoreMatchers.<Object>is("init"));
        assertThat(((InsertShardingCondition) actual.getShardingConditions().get(0)).getInsertValueExpression(), is("(?, ?)"));
        assertThat(((InsertShardingCondition) actual.getShardingConditions().get(1)).getInsertValueExpression(), is("(?, ?)"));
        assertThat(actual.getShardingConditions().get(0).getShardingValues().size(), is(1));
        assertThat(actual.getShardingConditions().get(1).getShardingValues().size(), is(1));
        assertShardingValue((ListShardingValue) actual.getShardingConditions().get(0).getShardingValues().get(0), 10);
        assertShardingValue((ListShardingValue) actual.getShardingConditions().get(1).getShardingValues().get(0), 11);
    }
    
    private void assertShardingValue(final ListShardingValue actual, final int expected) {
        assertThat(actual.getValues().size(), is(1));
        assertThat((int) actual.getValues().iterator().next(), is(expected));
    }
    
    @Test
    public void assertOptimizeWithValuesWithoutPlaceHolderWithGeneratedKey() {
        GeneratedKey generatedKey = new GeneratedKey(new Column("order_id", "t_order"));
        generatedKey.getGeneratedKeys().add(1);
        generatedKey.getGeneratedKeys().add(2);
        ShardingConditions actual = new InsertOptimizeEngine(shardingRule, insertStatementWithValuesWithoutPlaceHolder, Collections.emptyList(), generatedKey).optimize();
        assertThat(actual.getShardingConditions().size(), is(1));
        assertThat(((InsertShardingCondition) actual.getShardingConditions().get(0)).getParameters().size(), is(0));
        assertThat(((InsertShardingCondition) actual.getShardingConditions().get(0)).getInsertValueExpression(), is("(12,'a', 1)"));
        assertShardingValue((ListShardingValue) actual.getShardingConditions().get(0).getShardingValues().get(0), 1);
        assertShardingValue((ListShardingValue) actual.getShardingConditions().get(0).getShardingValues().get(1), 12);
    }
}
