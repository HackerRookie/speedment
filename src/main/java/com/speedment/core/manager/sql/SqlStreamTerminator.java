/**
 *
 * Copyright (c) 2006-2015, Speedment, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); You may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.speedment.core.manager.sql;

import com.speedment.codegen.base.Generator;
import com.speedment.core.manager.sql.generator.SQLGenerator;
import com.speedment.api.db.AsynchronousQueryResult;
import com.speedment.core.field.builders.AbstractCombinedBasePredicate.AndCombinedBasePredicate;
import com.speedment.api.field.builders.PredicateBuilder;
import com.speedment.util.Cast;
import com.speedment.core.stream.builder.action.Action;
import static com.speedment.core.stream.builder.action.Property.SIZE;
import static com.speedment.core.stream.builder.action.Verb.PRESERVE;
import com.speedment.core.stream.builder.action.reference.FilterAction;
import com.speedment.core.stream.builder.pipeline.DoublePipeline;
import com.speedment.core.stream.builder.pipeline.IntPipeline;
import com.speedment.core.stream.builder.pipeline.LongPipeline;
import com.speedment.core.stream.builder.pipeline.Pipeline;
import com.speedment.core.stream.builder.pipeline.ReferencePipeline;
import com.speedment.core.stream.builder.streamterminator.StreamTerminator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import static java.util.stream.Collectors.toList;
import com.speedment.api.field.builders.ComparablePredicateBuilder;
import static java.util.stream.Collectors.joining;

/**
 *
 * @author pemi
 * @param <ENTITY> the entity type
 */
public class SqlStreamTerminator<ENTITY> implements StreamTerminator {

    private final AbstractSqlManager<ENTITY> manager;
    private final AsynchronousQueryResult<ENTITY> asynchronousQueryResult;
    private final Generator generator; // Todo: Static?

    public SqlStreamTerminator(AbstractSqlManager<ENTITY> manager, final AsynchronousQueryResult<ENTITY> asynchronousQueryResult) {
        this.manager = manager;
        this.asynchronousQueryResult = asynchronousQueryResult;
        this.generator = new SQLGenerator();
    }

    @Override
    public <T extends Pipeline> T optimize(T initialPipeline) {
        final List<PredicateBuilder<?>> andPredicateBuilders = new ArrayList<>();

        for (Action<?, ?> action : initialPipeline.stream().collect(toList())) {
            @SuppressWarnings("rawtypes")
            final Optional<FilterAction> oFilterAction = Cast.cast(action, FilterAction.class);
            if (oFilterAction.isPresent()) {
                @SuppressWarnings("unchecked")
                final List<PredicateBuilder<?>> newAndPredicateBuilders = andPredicateBuilders(oFilterAction.get());
                andPredicateBuilders.addAll(newAndPredicateBuilders);
            } else {
                break; // We can only do initial consecutive FilterAction(s)
            }
        }

        if (!andPredicateBuilders.isEmpty()) {
            modifySource(andPredicateBuilders, asynchronousQueryResult);
        }
        return initialPipeline;
    }

    public void modifySource(final List<PredicateBuilder<?>> predicateBuilders, AsynchronousQueryResult<ENTITY> qr) {

        if (predicateBuilders.isEmpty()) {
            // Nothing to do...
            return;
        }
        final String sql = manager.sqlSelect(" where " + generator.onEach(predicateBuilders)
            .collect(joining(" AND ")));

        @SuppressWarnings("rawtypes")
        final List<Object> values = predicateBuilders.stream()
            .map(pb -> Cast.cast(pb, ComparablePredicateBuilder.class))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(ComparablePredicateBuilder::getValue)
            .collect(toList());

        qr.setSql(sql);
        qr.setValues(values);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<PredicateBuilder<?>> andPredicateBuilders(FilterAction<ENTITY> action) {
        final List<PredicateBuilder<?>> andPredicateBuilders = new ArrayList<>();
        final Predicate<? super ENTITY> predicate = action.getPredicate();

        final Optional<PredicateBuilder> oPredicateBuilder = Cast.cast(predicate, PredicateBuilder.class);
        if (oPredicateBuilder.isPresent()) {
            andPredicateBuilders.add(oPredicateBuilder.get()); // Just a top level predicate builder
        } else {

            final Optional<AndCombinedBasePredicate> oAndCombinedBasePredicate = Cast.cast(predicate, AndCombinedBasePredicate.class);
            if (oAndCombinedBasePredicate.isPresent()) {

                final AndCombinedBasePredicate<ENTITY> andCombinedBasePredicate = (AndCombinedBasePredicate<ENTITY>) oAndCombinedBasePredicate.get();
                andCombinedBasePredicate.stream()
                    .map(p -> Cast.cast(p, PredicateBuilder.class))
                    .filter(p -> p.isPresent())
                    .map(Optional::get)
                    .forEachOrdered(andPredicateBuilders::add);
            }
        }
        return andPredicateBuilders;
    }

    @Override
    public long count(DoublePipeline pipeline) {
        return countHelper(pipeline, () -> StreamTerminator.super.count(pipeline));
    }

    @Override
    public <T> long count(IntPipeline pipeline) {
        return countHelper(pipeline, () -> StreamTerminator.super.count(pipeline));
    }

    @Override
    public long count(LongPipeline pipeline) {
        return countHelper(pipeline, () -> StreamTerminator.super.count(pipeline));
    }

    @Override
    public <T> long count(ReferencePipeline<T> pipeline) {
        return countHelper(pipeline, () -> StreamTerminator.super.count(pipeline));
    }

    private static final Predicate<Action<?, ?>> CHECK_RETAIN_SIZE = action -> action.is(PRESERVE, SIZE);

    /**
     * Optimizer for count operations!
     *
     * @param pipeline
     * @param fallbackSupplier
     * @return the number of rows
     */
    private long countHelper(Pipeline pipeline, LongSupplier fallbackSupplier) {
        if (pipeline.stream().allMatch(CHECK_RETAIN_SIZE)) {
            final String sql = "select count(*) from " + manager.sqlTableReference();
            return manager.synchronousStreamOf(sql, Collections.emptyList(), rs -> rs.getLong(1)).findAny().get();
        }
        return fallbackSupplier.getAsLong();
    }
//
//    private Supplier<Stream<Entity>> supplier(Collection<Field> fields) {
//        final Dbms dbms = table.ancestor(Dbms.class).get();
//        final DbmsHandler dbmsHandler = Platform.get().get(DbmsHandlerComponent.class).get(dbms);
//        return () -> {
//            return dbmsHandler.executeQuery(null, null, null);
//        };
//    }

}
