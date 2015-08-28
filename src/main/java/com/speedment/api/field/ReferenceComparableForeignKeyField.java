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
package com.speedment.api.field;

import com.speedment.api.annotation.Api;

/**
 *
 * @author          pemi, Emil Forslund
 * @param <ENTITY>  the entity type
 * @param <V>       the field value type
 * @param <FK>      the foreign entity type
 */
@Api(version = "2.1")
public interface ReferenceComparableForeignKeyField<ENTITY, V extends Comparable<? super V>, FK> extends 
    ReferenceComparableField<ENTITY, V>, ReferenceForeignKeyField<ENTITY, V, FK> {}