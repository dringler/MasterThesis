package de.uni_mannheim.informatik.wdi.matching;

import de.uni_mannheim.informatik.wdi.model.Correspondence;
import de.uni_mannheim.informatik.wdi.model.DefaultRecord;
import de.uni_mannheim.informatik.wdi.model.DefaultSchemaElement;
import de.uni_mannheim.informatik.wdi.model.FeatureVectorDataSet;
import de.uni_mannheim.informatik.wdi.model.Matchable;
import de.uni_mannheim.informatik.wdi.model.Pair;
import de.uni_mannheim.informatik.wdi.model.ResultSet;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by Daniel Ringler
 */
/**
 * A {@link MatchingRule} that evaluates to the highest confidence in the group.
 *
 * Does not make use of schema-level correspondences
 *
 * @author Daniel Ringler
 *
 * @param <RecordType>
 */
public class MaximumCombinationMatchingRule<RecordType extends Matchable, SchemaElementType>
        extends MatchingRule<RecordType, SchemaElementType> {

    private static final long serialVersionUID = 1L;
    private List<Pair<Comparator<RecordType, SchemaElementType>, Double>> comparators;
    private double offset;

    /**
     * Initialises the rule. The finalThreshold determines the matching
     * decision.
     *
     * @param finalThreshold
     */
    public MaximumCombinationMatchingRule(double finalThreshold) {
        super(finalThreshold);
        comparators = new LinkedList<>();
    }

    /**
     * Initializes the rule. The offset is added to the similarities,
     * the finalThreshold determines the matching decision.
     *
     * @param offset
     * @param finalThreshold
     */
    public MaximumCombinationMatchingRule(double offset, double finalThreshold) {
        this(finalThreshold);
        this.offset = offset;
    }

    /**
     * Adds a comparator with the specified weight to this rule.
     *
     * @param comparator
     * @param weight
     *            a double value larger than 0.
     * @throws Exception
     */
    public void addComparator(Comparator<RecordType, SchemaElementType> comparator, double weight)
            throws Exception {
        if (weight > 0.0) {
            comparators.add(new Pair<Comparator<RecordType, SchemaElementType>, Double>(
                    comparator, weight));
        } else {
            throw new Exception("Weight cannot be 0.0 or smaller");
        }
    }

    /**
     * Normalize the weights of the different comparators so they sum up to 1.
     */
    public void normalizeWeights() {
        Double sum = 0.0;
        for (Pair<Comparator<RecordType, SchemaElementType>, Double> pair : comparators) {
            sum += pair.getSecond();
        }
        List<Pair<Comparator<RecordType, SchemaElementType>, Double>> normComparators = new LinkedList<>();
        for (Pair<Comparator<RecordType, SchemaElementType>, Double> pair : comparators) {
            normComparators.add(new Pair<Comparator<RecordType, SchemaElementType>, Double>(pair
                    .getFirst(), (pair.getSecond() / sum)));
        }
        comparators = normComparators;
    }

    @Override
    public Correspondence<RecordType, SchemaElementType> apply(RecordType record1, RecordType record2, ResultSet<Correspondence<SchemaElementType, RecordType>> schemaCorrespondences) {
        double highest = 0.0;
        //double wHighest = 0.0;
        boolean isFirst = true;
        for (int i = 0; i < comparators.size(); i++) {
            Pair<Comparator<RecordType, SchemaElementType>, Double> pair = comparators.get(i);

            Comparator<RecordType, SchemaElementType> comp = pair.getFirst();

            //double similarity = comp.compare(record1, record2, schemaCorrespondences);
            double similarity = comp.compare(record1, record2, null);
            double threshold = pair.getSecond();
            if (isFirst) {
                highest = similarity;// * weight;
                //wHighest = weight;
                isFirst = false;
            } else {
                if (similarity  > highest) {
                    highest = similarity;
                    //wHighest = weight;
                }
            }

        }
        //return offset + (sum / wSum);

        //double similarity = offset + (highest / wHighest);
        double similarity = offset + highest;

        if (similarity >= getFinalThreshold() && similarity > 0.0) {
            return createCorrespondence(record1, record2, schemaCorrespondences, similarity);
        } else {
            return null;
        }
    }

    @Override
    public DefaultRecord generateFeatures(RecordType record1, RecordType record2, ResultSet<Correspondence<SchemaElementType, RecordType>> schemaCorrespondences, FeatureVectorDataSet features) {
        DefaultRecord model = new DefaultRecord(String.format("%s-%s",
                record1.getIdentifier(), record2.getIdentifier()), this
                .getClass().getSimpleName());

        double sum = offset;

        for (int i = 0; i < comparators.size(); i++) {
            Pair<Comparator<RecordType, SchemaElementType>, Double> pair = comparators.get(i);

            Comparator<RecordType, SchemaElementType> comp = pair.getFirst();

            //double similarity = comp.compare(record1, record2, schemaCorrespondences);
            double similarity = comp.compare(record1, record2, null);
            double weight = pair.getSecond();

            sum += (similarity * weight);

            String name = String.format("[%d] %s", i, comp.getClass()
                    .getSimpleName());
            DefaultSchemaElement att = null;
            for(DefaultSchemaElement elem : features.getAttributes()) {
                if(elem.toString().equals(name)) {
                    att = elem;
                }
            }
            if(att==null) {
                att = new DefaultSchemaElement(name);
            }
            model.setValue(att, Double.toString(similarity));
        }

        model.setValue(FeatureVectorDataSet.ATTRIBUTE_FINAL_VALUE, Double.toString(sum));
        model.setValue(FeatureVectorDataSet.ATTRIBUTE_IS_MATCH, Boolean.toString(sum >= getFinalThreshold()));

        return model;
    }
}
