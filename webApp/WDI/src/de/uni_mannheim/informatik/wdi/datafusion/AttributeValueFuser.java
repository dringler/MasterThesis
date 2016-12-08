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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import de.uni_mannheim.informatik.wdi.clustering.ConnectedComponentClusterer;
import de.uni_mannheim.informatik.wdi.datafusion.conflictresolution.ConflictResolutionFunction;
import de.uni_mannheim.informatik.wdi.model.Correspondence;
import de.uni_mannheim.informatik.wdi.model.Fusable;
import de.uni_mannheim.informatik.wdi.model.FusableDataSet;
import de.uni_mannheim.informatik.wdi.model.FusableValue;
import de.uni_mannheim.informatik.wdi.model.FusedValue;
import de.uni_mannheim.informatik.wdi.model.Matchable;
import de.uni_mannheim.informatik.wdi.model.Pair;
import de.uni_mannheim.informatik.wdi.model.RecordGroup;
import de.uni_mannheim.informatik.wdi.model.ResultSet;
import de.uni_mannheim.informatik.wdi.model.Triple;

/**
 * Abstract super class for all Fusers tailored to specific attributes (hence the ValueType). Ignores schema correspondences.
 * @author Oliver Lehmberg (oli@dwslab.de)
 *
 * @param <ValueType>
 * @param <RecordType>
 */
public abstract class AttributeValueFuser<ValueType, RecordType extends Matchable & Fusable<SchemaElementType>, SchemaElementType> extends AttributeFuser<RecordType, SchemaElementType> {

	/**
	 * Collects all fusable values from the group of records
	 * @param group
	 * @return
	 */
	protected List<FusableValue<ValueType, RecordType, SchemaElementType>> getFusableValues(RecordGroup<RecordType, SchemaElementType> group, ResultSet<Correspondence<SchemaElementType, RecordType>> schemaCorrespondences, SchemaElementType schemaElement) {
		List<FusableValue<ValueType, RecordType, SchemaElementType>> values = new LinkedList<>();
		
		for(Pair<RecordType, FusableDataSet<RecordType, SchemaElementType>> p : group.getRecordsWithDataSets()) {
			// only consider existing values
			RecordType record = p.getFirst();
			Correspondence<SchemaElementType, RecordType> correspondence = group.getSchemaCorrespondenceForRecord(p.getFirst(), schemaCorrespondences, schemaElement); 
			if(hasValue(record, correspondence)) {
				ValueType v = getValue(record, correspondence);
				FusableValue<ValueType, RecordType, SchemaElementType> value = new FusableValue<ValueType, RecordType, SchemaElementType>(v, p.getFirst(), p.getSecond());
				values.add(value);
			}
		}
		
		return values;
	}
	
	/**
	 * returns the value that is used by this fuser from the given record. Required for the collection of fusable values.
	 * @param record
	 * @return
	 */
	protected abstract ValueType getValue(RecordType record, Correspondence<SchemaElementType, RecordType> correspondence);
	
	@Override
	public Double getConsistency(RecordGroup<RecordType, SchemaElementType> group,
			EvaluationRule<RecordType, SchemaElementType> rule, ResultSet<Correspondence<SchemaElementType, RecordType>> schemaCorrespondences, SchemaElementType schemaElement) {
		
		List<RecordType> records = new ArrayList<>(group.getRecords());

		// remove non-existing values
		Iterator<RecordType> it = records.iterator();
		while(it.hasNext()) {
			RecordType record = it.next();
			Correspondence<SchemaElementType, RecordType> cor = group.getSchemaCorrespondenceForRecord(record, schemaCorrespondences, schemaElement);

			if(cor==null || !hasValue(record, cor)) {
				it.remove();
			}
		}
		
		if(records.size()==0) {
			return null;
		} else if(records.size()==1) {
			return 1.0; // this record group is consistent, as there is only one value
		}
		
		ConnectedComponentClusterer<RecordType> con = new ConnectedComponentClusterer<>();
		
		// calculate pair-wise similarities
//		LinkedList<Triple<RecordType, RecordType, Double>> similarities = new LinkedList<>();
		for(int i=0; i<records.size();i++) {
			RecordType r1 = records.get(i);
			Correspondence<SchemaElementType, RecordType> cor1 = group.getSchemaCorrespondenceForRecord(r1, schemaCorrespondences, schemaElement);
			if(cor1!=null) {
				for(int j=i+1; j<records.size(); j++) {
					RecordType r2 = records.get(j);
					Correspondence<SchemaElementType, RecordType> cor2 = group.getSchemaCorrespondenceForRecord(r2, schemaCorrespondences, schemaElement);
				
					if(cor2!=null && !con.isEdgeAlreadyInCluster(r1, r2)) {
					
						//TODO this calculation is quadratic, meaning it does not finish in reasonable time on large data sets
						// try to improve the runtime (maybe by combining it with the clustering that follows to exclude pairs from calculation)
						// assumption: in fusion we have a target schema, so all schema correspondences refer to the target schema 
						// this means that we can simply combine both schema correspondences to get a schema correspondence between the two records
						Correspondence<SchemaElementType, RecordType> cor = Correspondence.<SchemaElementType, RecordType>combine(cor1, cor2);
						
						if(rule.isEqual(r1, r2, cor)) {
							//similarities.add(new Triple<>(r1, r2, 1.0));
							con.addEdge(new Triple<>(r1, r2, 1.0));
						}
					
					}
				}
			}
		}
		
//		ConnectedComponentClusterer<RecordType> con = new ConnectedComponentClusterer<>();
//		Map<Collection<RecordType>, RecordType> clusters = con.cluster(similarities);
		Map<Collection<RecordType>, RecordType> clusters = con.createResult();
		int largestClusterSize = 0;
		for(Collection<RecordType> cluster : clusters.keySet()) {
			if(cluster.size()>largestClusterSize) {
				largestClusterSize = cluster.size();
			}
		}
		
		if(largestClusterSize>group.getSize()) {
			System.out.println("Wrong cluster!");
		}
		
		//return (double)largestClusterSize / (double)group.getSize();
		return (double)largestClusterSize / (double)records.size();
	}
	
	private ConflictResolutionFunction<ValueType, RecordType, SchemaElementType> conflictResolution;
	
	/**
	 * Creates an instance, specifies the conflict resolution function to use
	 * @param conflictResolution
	 */
	public AttributeValueFuser(ConflictResolutionFunction<ValueType, RecordType, SchemaElementType> conflictResolution) {
		this.conflictResolution = conflictResolution;
	}

	/**
	 * Returns the fused value by applying the conflict resolution function to the list of fusable values
	 * @param group
	 * @return
	 */
	protected FusedValue<ValueType, RecordType, SchemaElementType> getFusedValue(RecordGroup<RecordType, SchemaElementType> group, ResultSet<Correspondence<SchemaElementType, RecordType>> schemaCorrespondences, SchemaElementType schemaElement) {
		return conflictResolution.resolveConflict(getFusableValues(group, schemaCorrespondences, schemaElement));
	}
}
