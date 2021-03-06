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
package de.uni_mannheim.informatik.wdi.matching.blocking;

import de.uni_mannheim.informatik.wdi.matching.MatchingTask;
import de.uni_mannheim.informatik.wdi.model.Correspondence;
import de.uni_mannheim.informatik.wdi.model.DataSet;
import de.uni_mannheim.informatik.wdi.model.Matchable;
import de.uni_mannheim.informatik.wdi.model.Pair;
import de.uni_mannheim.informatik.wdi.model.Record;
import de.uni_mannheim.informatik.wdi.model.ResultSet;
import de.uni_mannheim.informatik.wdi.processing.DataProcessingEngine;
import de.uni_mannheim.informatik.wdi.processing.Function;

/**
 * Implementation of a simple {@link Blocker} based on blocking keys, where only
 * {@link Record}s with an equal blocking key (generated by the
 * {@link BlockingKeyGenerator}) are compared.
 * 
 * @author Oliver Lehmberg (oli@dwslab.de)
 * @author Robert Meusel (robert@dwslab.de)
 * 
 * @param <RecordType>
 */
public class StandardBlocker<RecordType extends Matchable, SchemaElementType extends Matchable> extends Blocker<RecordType, SchemaElementType> {

	private BlockingKeyGenerator<RecordType> blockingFunction;
	private BlockingKeyGenerator<RecordType> secondBlockingFunction;
//	private Map<String, List<RecordType>> blocks;
//	private Map<String, List<RecordType>> blocks2;
//	
//	private void initializeBlocks(DataSet<RecordType, SchemaElementType> dataset, DataProcessingEngine engine) {
//		
//		System.out.println("Calculating blocking keys");
//		
//		KeyBasedBlockingPreprocessor<RecordType> blocker = new KeyBasedBlockingPreprocessor<>();
//		
//		blocker.setBlockingFunction(blockingFunction);
//		
//		engine.iterateDataset(dataset, blocker);
//		
//		blocks = blocker.getBlocks();
//	}
//	
//	private void initializeBlocks(DataSet<RecordType, SchemaElementType> dataset1, DataSet<RecordType, SchemaElementType> dataset2, DataProcessingEngine engine) {
//
//		KeyBasedBlockingPreprocessor<RecordType> blocker = new KeyBasedBlockingPreprocessor<>();
//		blocker.setBlockingFunction(blockingFunction);
//
//		System.out.println("Calculating blocking keys for dataset 1");
//
//		engine.iterateDataset(dataset1, blocker);
//		blocks = blocker.getBlocks();
//
//		System.out.println("Calculating blocking keys for dataset 2");
//
//		if(secondBlockingFunction!=null) {
//			blocker.setBlockingFunction(secondBlockingFunction);
//		}
//
//		engine.iterateDataset(dataset2, blocker);
//		blocks2 = blocker.getBlocks();
//	}
	
	public StandardBlocker(BlockingKeyGenerator<RecordType> blockingFunction) {
		this.blockingFunction = blockingFunction;
		this.secondBlockingFunction = blockingFunction;
	}
	
	/**
	 * 
	 * Creates a new Standard Blocker with the given blocking function(s). 
	 * If two datasets are used and secondBlockingFunction is not null, secondBlockingFunction will be used for the second dataset. If it is null, blockingFunction will be used for both datasets 
	 * 
	 * @param blockingFunction
	 * @param secondBlockingFunction
	 */
	public StandardBlocker(BlockingKeyGenerator<RecordType> blockingFunction, BlockingKeyGenerator<RecordType> secondBlockingFunction) {
		this.blockingFunction = blockingFunction;
		this.secondBlockingFunction = secondBlockingFunction == null ? blockingFunction : secondBlockingFunction;
	}


	/* (non-Javadoc)
	 * @see de.uni_mannheim.informatik.wdi.matching.blocking.Blocker#runBlocking(de.uni_mannheim.informatik.wdi.model.DataSet, de.uni_mannheim.informatik.wdi.model.DataSet, de.uni_mannheim.informatik.wdi.model.ResultSet, de.uni_mannheim.informatik.wdi.matching.MatchingEngine)
	 */
	@Override
	public ResultSet<BlockedMatchable<RecordType, SchemaElementType>> runBlocking(
			DataSet<RecordType, SchemaElementType> dataset1,
			DataSet<RecordType, SchemaElementType> dataset2,
			ResultSet<Correspondence<SchemaElementType, RecordType>> schemaCorrespondences,
			DataProcessingEngine engine,
            boolean blockFiltering,
            double r){
		ResultSet<BlockedMatchable<RecordType, SchemaElementType>> result = new ResultSet<>();
		
		Function<String, RecordType> joinKeyGenerator1 = new Function<String, RecordType>() {

			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			@Override
			public String execute(RecordType input) {
				return blockingFunction.getBlockingKey(input);
			}
		};
		
		Function<String, RecordType> joinKeyGenerator2 = new Function<String, RecordType>() {

			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			@Override
			public String execute(RecordType input) {
				return secondBlockingFunction.getBlockingKey(input);
			}
		};
		
		for(Pair<RecordType, RecordType> p : engine.join(dataset1, dataset2, joinKeyGenerator1, joinKeyGenerator2, blockFiltering, r).get()) {
			result.add(new MatchingTask<RecordType, SchemaElementType>(p.getFirst(), p.getSecond(), schemaCorrespondences));
		}
		
//		initializeBlocks(dataset1, dataset2, engine);
//		
//		for(String key1 : blocks.keySet()) {
//			List<RecordType> block = blocks.get(key1);
//			List<RecordType> block2 = blocks2.get(key1);
//			
//			if(block2!=null) {
//				
//				for(RecordType r1 : block) {
//					for(RecordType r2 : block2) {
//						result.add(new MatchingTask<RecordType, SchemaElementType>(r1, r2, schemaCorrespondences));
//					}
//				}
//				
//			}
//			
//		}
        ;


		calculatePerformance(dataset1, dataset2, result);
		
		return result;
	}

	/* (non-Javadoc)
	 * @see de.uni_mannheim.informatik.wdi.matching.blocking.Blocker#runBlocking(de.uni_mannheim.informatik.wdi.model.DataSet, boolean, de.uni_mannheim.informatik.wdi.model.ResultSet, de.uni_mannheim.informatik.wdi.matching.MatchingEngine)
	 */
	@Override
	public ResultSet<BlockedMatchable<RecordType, SchemaElementType>> runBlocking(
			DataSet<RecordType, SchemaElementType> dataset,
			boolean isSymmetric,
			ResultSet<Correspondence<SchemaElementType, RecordType>> schemaCorrespondences,
			DataProcessingEngine engine) {
		ResultSet<BlockedMatchable<RecordType, SchemaElementType>> result = new ResultSet<>();
		
		Function<String, RecordType> joinKeyGenerator1 = new Function<String, RecordType>() {

			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			@Override
			public String execute(RecordType input) {
				return blockingFunction.getBlockingKey(input);
			}
		};
		
		for(Pair<RecordType, RecordType> p : engine.symmetricSelfJoin(dataset, joinKeyGenerator1).get()) {
			result.add(new MatchingTask<RecordType, SchemaElementType>(p.getFirst(), p.getSecond(), schemaCorrespondences));
		}
		
//		initializeBlocks(dataset, engine);
//		
//		for(List<RecordType> block : blocks.values()) {
//			for(int i = 0; i < block.size(); i++) {
//				for(int j = isSymmetric ? i+1 : 0; j<block.size(); j++) {
//					if(i!=j) {
//						result.add(new MatchingTask<RecordType, SchemaElementType>(block.get(i), block.get(j), schemaCorrespondences));
//					}
//				}
//			}
//		}
		//result.deduplicate();
		calculatePerformance(dataset, dataset, result);
		
		return result;
	}

}
