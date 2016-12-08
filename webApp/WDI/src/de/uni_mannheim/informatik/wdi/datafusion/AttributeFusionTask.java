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
import de.uni_mannheim.informatik.wdi.model.Fusable;
import de.uni_mannheim.informatik.wdi.model.Matchable;
import de.uni_mannheim.informatik.wdi.model.RecordGroup;
import de.uni_mannheim.informatik.wdi.model.ResultSet;

/**
 * Contains all information that is necessary to perform the fusion.
 * Includes a fuser for a specific schema element in the target schema which can be evaluated with the given rule.
 * Further contains all schema correspondences for the target schema element (a record group that is fused can contain records from any number of datasets). 
 * 
 * @author Oliver Lehmberg (oli@dwslab.de)
 *
 */
public class AttributeFusionTask<RecordType extends Matchable & Fusable<SchemaElementType>, SchemaElementType> {

	private SchemaElementType schemaElement;
	private AttributeFuser<RecordType, SchemaElementType> fuser;
	private ResultSet<Correspondence<SchemaElementType, RecordType>> correspondences;
	private EvaluationRule<RecordType, SchemaElementType> evaluationRule;
	
	/**
	 * @return the fuser
	 */
	public AttributeFuser<RecordType, SchemaElementType> getFuser() {
		return fuser;
	}
	
	/**
	 * @param fuser the fuser to set
	 */
	public void setFuser(AttributeFuser<RecordType, SchemaElementType> fuser) {
		this.fuser = fuser;
	}
	
	/**
	 * @return the correspondences
	 */
	public ResultSet<Correspondence<SchemaElementType, RecordType>> getCorrespondences() {
		return correspondences;
	}
	
	/**
	 * @param correspondences the correspondences to set
	 */
	public void setCorrespondences(
			ResultSet<Correspondence<SchemaElementType, RecordType>> correspondences) {
		this.correspondences = correspondences;
	}
	
	/**
	 * @return the evaluationRule
	 */
	public EvaluationRule<RecordType, SchemaElementType> getEvaluationRule() {
		return evaluationRule;
	}
	
	/**
	 * @param evaluationRule the evaluationRule to set
	 */
	public void setEvaluationRule(EvaluationRule<RecordType, SchemaElementType> evaluationRule) {
		this.evaluationRule = evaluationRule;
	}
	
	/**
	 * Returns the corresponding schema element in the target schema (the attribute to which the values are fused)
	 * @return the schemaElement
	 */
	public SchemaElementType getSchemaElement() {
		return schemaElement;
	}
	
	/**
	 * @param schemaElement the schemaElement to set
	 */
	public void setSchemaElement(SchemaElementType schemaElement) {
		this.schemaElement = schemaElement;
	}
	
	public void execute(RecordGroup<RecordType, SchemaElementType> group, RecordType fusedRecord) {
		fuser.fuse(group, fusedRecord, correspondences, schemaElement);
	}
	
}
