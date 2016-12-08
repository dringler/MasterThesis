/** 
 *
 * Copyright (C) 2015 Data and Web Science Group, University of Mannheim, Germany (code@dwslab.de)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * 		http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package de.uni_mannheim.informatik.wdi.datafusion;

import de.uni_mannheim.informatik.wdi.model.Correspondence;

/**
 * Abstract super class for all evaluation rules
 * @author Oliver Lehmberg (oli@dwslab.de)
 *
 * @param <RecordType>
 */
public abstract class EvaluationRule<RecordType, SchemaElementType> {

	/**
	 * Returns whether the values of the given records are equal according to this evaluation rule (assuming both records are in the target schema)
	 * @param record1
	 * @param record2
	 * @return
	 */
	public abstract boolean isEqual(RecordType record1, RecordType record2, SchemaElementType schemaElement);
	
	/**
	 * 
	 * Returns whether the values of the given records are equal according to this evaluation rule (assuming the records are in different schemas)
	 * @param record1
	 * @param record2
	 * @param schemaCorrespondence
	 * @return
	 */
	public abstract boolean isEqual(RecordType record1, RecordType record2, Correspondence<SchemaElementType, RecordType> schemaCorrespondence);
	
	
}
