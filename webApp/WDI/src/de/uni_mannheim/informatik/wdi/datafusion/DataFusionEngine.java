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

import java.util.HashMap;
import java.util.Map;

import de.uni_mannheim.informatik.wdi.model.Correspondence;
import de.uni_mannheim.informatik.wdi.model.Fusable;
import de.uni_mannheim.informatik.wdi.model.FusableDataSet;
import de.uni_mannheim.informatik.wdi.model.Matchable;
import de.uni_mannheim.informatik.wdi.model.RecordGroup;
import de.uni_mannheim.informatik.wdi.model.ResultSet;
import de.uni_mannheim.informatik.wdi.utils.ProgressReporter;

/**
 * Executer class to run the data fusion based on a selected
 * {@link DataFusionStrategy}.
 * 
 * @author Oliver Lehmberg (oli@dwslab.de)
 * 
 * @param <RecordType>
 */
public class DataFusionEngine<RecordType extends Matchable & Fusable<SchemaElementType>, SchemaElementType extends Matchable> {

	private DataFusionStrategy<RecordType, SchemaElementType> strategy;

	/**
	 * @return the strategy
	 */
	public DataFusionStrategy<RecordType, SchemaElementType> getStrategy() {
		return strategy;
	}
	
	/**
	 * Creates a new instance that uses the specified data fusion strategy.
	 * 
	 * @param strategy
	 */
	public DataFusionEngine(DataFusionStrategy<RecordType, SchemaElementType> strategy) {
		this.strategy = strategy;
	}

	/**
	 * Runs the data fusion process on the provided set of correspondences
	 * 
	 * @param correspondences
	 * @return a {@link FusableDataSet} based on the RecordType of the
	 *         {@link CorrespondenceSet}
	 */
	public FusableDataSet<RecordType, SchemaElementType> run(
			CorrespondenceSet<RecordType, SchemaElementType> correspondences,
			ResultSet<Correspondence<SchemaElementType, RecordType>> schemaCorrespondences) {
		FusableDataSet<RecordType, SchemaElementType> fusedDataSet = new FusableDataSet<>();

		for (RecordGroup<RecordType, SchemaElementType> clu : correspondences.getRecordGroups()) {
			RecordType fusedRecord = strategy.apply(clu, schemaCorrespondences);
			fusedDataSet.addRecord(fusedRecord);

			for (RecordType record : clu.getRecords()) {
				fusedDataSet.addOriginalId(fusedRecord, record.getIdentifier());
			}
		}

		return fusedDataSet;
	}

	/**
	 * Calculates the consistencies of the attributes of the records in the
	 * given correspondence set according to the data fusion strategy
	 * 
	 * @param correspondences
	 * @return
	 */
	public Map<String, Double> getAttributeConsistencies(
			CorrespondenceSet<RecordType, SchemaElementType> correspondences,
			ResultSet<Correspondence<SchemaElementType, RecordType>> schemaCorrespondences) {
		Map<String, Double> consistencySums = new HashMap<>(); // = sum of consistency values
		Map<String, Integer> consistencyCounts = new HashMap<>(); // = number of instances

		ProgressReporter progress = new ProgressReporter(correspondences.getRecordGroups().size(), "Calculating consistencies");
		
		// changed to calculation as follows:
		// degree of consistency per instance = percentage of most frequent value
		// consistency = average of degree of consistency per instance
		
		// for each record group (=instance in the target dataset), calculate the degree of consistency for each attribute
		for (RecordGroup<RecordType, SchemaElementType> clu : correspondences.getRecordGroups()) {

			Map<String, Double> values = strategy
					.getAttributeConsistency(clu, schemaCorrespondences);

			for (String att : values.keySet()) {
				Double consistencyValue = values.get(att);
				
				if(consistencyValue!=null) {
					Integer cnt = consistencyCounts.get(att);
					if (cnt == null) {
						cnt = 0;
					}
					consistencyCounts.put(att, cnt + 1);
					
					Double sum = consistencySums.get(att);
					if(sum == null) {
						sum = 0.0;
					}
					consistencySums.put(att, sum + consistencyValue);
				}
			}

			progress.incrementProgress();
			progress.report();
		}

		Map<String, Double> result = new HashMap<>();
		for (String att : consistencySums.keySet()) {
			if(consistencySums.get(att)!=null) {
				// divide by count, not total number of record groups as we only consider groups that actually have a value
				double consistency = consistencySums.get(att)
						/ (double) consistencyCounts.get(att);
				
				result.put(att, consistency);
			}
		}

		return result;
	}

	/**
	 * Calculates the consistencies of the attributes of the records in the
	 * given correspondence set according to the data fusion strategy and prints
	 * the results to the console
	 * 
	 * @param correspondences
	 */
	public void printClusterConsistencyReport(
			CorrespondenceSet<RecordType, SchemaElementType> correspondences,
			ResultSet<Correspondence<SchemaElementType, RecordType>> schemaCorrespondences) {
		System.out.println("Attribute Consistencies:");
		Map<String, Double> consistencies = getAttributeConsistencies(correspondences, schemaCorrespondences);
		for (String att : consistencies.keySet()) {
			System.out.println(String.format("\t%s: %.2f", att,
					consistencies.get(att)));
		}
	}
}
