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

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import de.uni_mannheim.informatik.wdi.model.Fusable;
import de.uni_mannheim.informatik.wdi.model.FusableDataSet;
import de.uni_mannheim.informatik.wdi.model.Matchable;
import de.uni_mannheim.informatik.wdi.model.RecordGroup;
import de.uni_mannheim.informatik.wdi.model.RecordGroupFactory;

/**
 * Represents a set of correspondences (from the identity resolution)
 * 
 * @author Oliver Lehmberg (oli@dwslab.de)
 * @author Robert Meusel (robert@dwslab.de)
 * 
 * @param <RecordType>
 */
public class CorrespondenceSet<RecordType extends Matchable & Fusable<SchemaElementType>, SchemaElementType> {

	private Collection<RecordGroup<RecordType, SchemaElementType>> groups;
	private Map<String, RecordGroup<RecordType, SchemaElementType>> recordIndex;

	private RecordGroupFactory<RecordType, SchemaElementType> groupFactory;
	
	public CorrespondenceSet() {
		groups = new LinkedList<>();
		recordIndex = new HashMap<>();
		groupFactory = new RecordGroupFactory<>();
	}
	
	/**
	 * @param groupFactory the groupFactory to set
	 */
	public void setGroupFactory(
			RecordGroupFactory<RecordType, SchemaElementType> groupFactory) {
		this.groupFactory = groupFactory;
	}

	/**
	 * Loads correspondences from a file and adds them to this correspondence
	 * set. Can be called multiple times.
	 * 
	 * @param correspondenceFile
	 * @param first
	 * @param second
	 * @throws IOException
	 */
	public void loadCorrespondences(File correspondenceFile,
			FusableDataSet<RecordType, SchemaElementType> first, FusableDataSet<RecordType, SchemaElementType> second)
			throws IOException {
		CSVReader reader = new CSVReader(new FileReader(correspondenceFile));

		String[] values = null;

		while ((values = reader.readNext()) != null) {
			// check if the ids exist in the provided datasets
			if (first.getRecord(values[0]) == null) {
				System.err.println(String.format(
						"Record %s not found in first dataset", values[0]));
				continue;
			}
			if (second.getRecord(values[1]) == null) {
				System.err.println(String.format(
						"Record %s not found in second dataset", values[0]));
				continue;
			}

			// check if the ids already belong to any groups
			RecordGroup<RecordType, SchemaElementType> grp1 = recordIndex.get(values[0]);
			RecordGroup<RecordType, SchemaElementType> grp2 = recordIndex.get(values[1]);

			if (grp1 == null && grp2 == null) {
				// no existing groups, create a new one
				RecordGroup<RecordType, SchemaElementType> grp = groupFactory.createRecordGroup();
				grp.addRecord(values[0], first);
				grp.addRecord(values[1], second);
				recordIndex.put(values[0], grp);
				recordIndex.put(values[1], grp);
				groups.add(grp);
			} else if (grp1 != null && grp2 == null) {
				// one existing group, add to this group
				grp1.addRecord(values[1], second);
				recordIndex.put(values[1], grp1);
			} else if (grp1 == null && grp2 != null) {
				// one existing group, add to this group
				grp2.addRecord(values[0], first);
				recordIndex.put(values[0], grp2);
			} else {
				// two existing groups, merge
				grp1.mergeWith(grp2);

				for (String id : grp2.getRecordIds()) {
					recordIndex.put(id, grp1);
				}
			}
		}

		reader.close();
	}

	/**
	 * Loads correspondences from a file and adds them to this correspondence
	 * set. Can be called multiple times.
	 * 
	 * @param correspondenceFile
	 * @param first
	 * @param second
	 * @throws IOException
	 */
	public void loadCorrespondences(File correspondenceFile,
			FusableDataSet<RecordType, SchemaElementType> first)
			throws IOException {
		CSVReader reader = new CSVReader(new FileReader(correspondenceFile));

		String[] values = null;
		int skipped = 0;

		while ((values = reader.readNext()) != null) {
			// check if the ids exist in the provided datasets
			if (first.getRecord(values[0]) == null) {
				skipped++;
//				System.err.println(String.format(
//						"Record %s not found in first dataset", values[0]));
				continue;
			}
//			if (second.getRecord(values[1]) == null) {
//				System.err.println(String.format(
//						"Record %s not found in second dataset", values[0]));
//				continue;
//			}

			// check if the ids already belong to any groups
//			RecordGroup<RecordType, SchemaElementType> grp1 = recordIndex.get(values[0]);
			
			// we only have the records from the source datasets, so we group by the id in the target dataset
			RecordGroup<RecordType, SchemaElementType> grp2 = recordIndex.get(values[1]);

			if (grp2 == null) {
				// no existing groups, create a new one
				RecordGroup<RecordType, SchemaElementType> grp = groupFactory.createRecordGroup();
				grp.addRecord(values[0], first);
//				grp.addRecord(values[1], second);
//				recordIndex.put(values[0], grp);
				recordIndex.put(values[1], grp);
				groups.add(grp);
//			} else if (grp1 != null && grp2 == null) {
//				// one existing group, add to this group
//				grp1.addRecord(values[1], second);
//				recordIndex.put(values[1], grp1);
			} else {
				// one existing group, add to this group
				grp2.addRecord(values[0], first);
				recordIndex.put(values[0], grp2);
			}
//			} else {
//				// two existing groups, merge
//				grp1.mergeWith(grp2);
//
//				for (String id : grp2.getRecordIds()) {
//					recordIndex.put(id, grp1);
//				}
//			}
		}

		reader.close();
		
		if (skipped>0) {
			System.err.println(String.format("Skipped %,d records (not found in provided dataset)", skipped));
		}
	}
	
	/**
	 * returns the groups of records which are the same according to the
	 * correspondences
	 * 
	 * @return
	 */
	public Collection<RecordGroup<RecordType, SchemaElementType>> getRecordGroups() {
		return groups;
	}

	/**
	 * writes the distribution of the sizes of the groups of records to the
	 * specified file
	 * 
	 * @param outputFile
	 * @throws IOException
	 */
	public void writeGroupSizeDistribution(File outputFile) throws IOException {
		Map<Integer, Integer> sizeDist = new HashMap<>();

		for (RecordGroup<RecordType, SchemaElementType> grp : groups) {
			int size = grp.getSize();

			Integer count = sizeDist.get(size);

			if (count == null) {
				count = 0;
			}

			sizeDist.put(size, ++count);
		}

		CSVWriter writer = new CSVWriter(new FileWriter(outputFile));

		writer.writeNext(new String[] { "Group Size", "Frequency" });
		for (int size : sizeDist.keySet()) {
			writer.writeNext(new String[] { Integer.toString(size),
					Integer.toString(sizeDist.get(size)) });
		}

		writer.close();
	}

	/**
	 * prints the distribution of the sizes of the groups of records to the
	 * specified file
	 * 
	 * @param outputFile
	 * @throws IOException
	 */
	public void printGroupSizeDistribution() throws IOException {
		Map<Integer, Integer> sizeDist = new HashMap<>();

		for (RecordGroup<RecordType, SchemaElementType> grp : groups) {
			int size = grp.getSize();

			Integer count = sizeDist.get(size);

			if (count == null) {
				count = 0;
			}

			sizeDist.put(size, ++count);
		}
		System.out.println("Group Size Distribtion of " + groups.size() + " groups:");
		System.out.println("	Group Size | Frequency ");
		System.out.println("	———————————————————————");

		for (int size : sizeDist.keySet()) {
			String sizeStr = Integer.toString(size);
			System.out.print("	");
			for (int i = 0; i < 10 - sizeStr.length(); i++) {
				System.out.print(" ");
			}
			System.out.print(sizeStr);
			System.out.print(" |");
			String countStr = Integer.toString(sizeDist.get(size));
			for (int i = 0; i < 10 - countStr.length(); i++) {
				System.out.print(" ");
			}
			System.out.println(countStr);
		}

	}

}
