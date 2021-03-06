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
package de.uni_mannheim.informatik.wdi.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Contains the evaluation performance for counts that exceed the range of int.
 * 
 * @author Oliver Lehmberg (oli@dwslab.de)
 * 
 */
public class BigPerformance extends Performance {

	private BigDecimal correct;
	private BigDecimal created;
	private BigDecimal correct_total;
	private int scale = 10;
	
	/**
	 * @param scale the scale to set
	 */
	public void setScale(int scale) {
		this.scale = scale;
	}
	/**
	 * @return the scale
	 */
	public int getScale() {
		return scale;
	}
	
	public BigPerformance(BigDecimal correct, BigDecimal created, BigDecimal correct_total) {
		super(0,0,0);
		this.correct = correct;
		this.created = created;
		this.correct_total = correct_total;
	}

	/**
	 * Returns the Precision
	 * 
	 * @return
	 */
	public double getPrecision() {
		if(created.equals(BigDecimal.ZERO)) {
			return 0.0;
		} else {
			return correct.setScale(getScale()).divide(created, RoundingMode.HALF_UP).doubleValue();
		}
	}

	/**
	 * Returns the Recall
	 * 
	 * @return
	 */
	public double getRecall() {
		if(correct_total.equals(BigDecimal.ZERO)) {
			return 0.0;
		} else {
			return correct.setScale(getScale()).divide(correct_total, RoundingMode.HALF_UP).doubleValue();
		}
	}

	/**
	 * Returns the F1-Measure
	 * 
	 * @return
	 */
	public double getF1() {
		if(getPrecision()==0 || getRecall()==0) {
			return 0.0;
		} else {
			return (2 * getPrecision() * getRecall())
				/ (getPrecision() + getRecall());
		}
	}

	public int getNumberOfPredicted() {
		return created.intValue();
	}
	
	public int getNumberOfCorrectlyPredicted() {
		return correct.intValue();
	}
	
	public int getNumberOfCorrectTotal() {
		return correct_total.intValue();
	}
}
