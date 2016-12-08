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
package de.uni_mannheim.informatik.wdi.usecase.events.identityresolution;

import de.uni_mannheim.informatik.wdi.matching.Comparator;
import de.uni_mannheim.informatik.wdi.model.Correspondence;
import de.uni_mannheim.informatik.wdi.model.DefaultSchemaElement;
import de.uni_mannheim.informatik.wdi.similarity.string.LevenshteinSimilarity;
import de.uni_mannheim.informatik.wdi.usecase.events.model.Movie;

/**
 * {@link Comparator} for {@link Movie}s based on the
 * {@link Movie#getDirector()} values, and their {@link LevenshteinSimilarity}
 * similarity.
 * 
 * @author Oliver Lehmberg (oli@dwslab.de)
 * 
 */
public class MovieDirectorComparatorLevenshtein extends Comparator<Movie, DefaultSchemaElement> {

	private static final long serialVersionUID = 1L;
	private LevenshteinSimilarity sim = new LevenshteinSimilarity();

	@Override
	public double compare(
			Movie record1,
			Movie record2,
			Correspondence<DefaultSchemaElement, Movie> schemaCorrespondences) {
		return sim.calculate(record1.getDirector(), record2.getDirector());
	}


}
