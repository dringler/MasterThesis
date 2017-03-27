package de.uni_mannheim.informatik.wdi.usecase.events.identityresolution;

import de.uni_mannheim.informatik.wdi.matching.Comparator;
import de.uni_mannheim.informatik.wdi.model.Correspondence;
import de.uni_mannheim.informatik.wdi.model.DefaultSchemaElement;
import de.uni_mannheim.informatik.wdi.similarity.BestListSimilarity;
import de.uni_mannheim.informatik.wdi.similarity.string.LevenshteinEditDistance;
import de.uni_mannheim.informatik.wdi.usecase.events.model.Event;

/**
 * {@link Comparator} for {@link Event}s based on the {@link Event#getUris()} ()}
 * value and their {@link LevenshteinEditDistance} value.
 * Based on the MovieTitleComparatorLevenshtein class
 *
 * @author Daniel Ringler
 *
 */
public class EventURIComparatorLevenshteinEditDistance  extends Comparator<Event, DefaultSchemaElement> {
    private static final long serialVersionUID = 1L;

    private BestListSimilarity bestListSimilarity = new BestListSimilarity();
    private LevenshteinEditDistance sim = new LevenshteinEditDistance();
    private double threshold;

    public EventURIComparatorLevenshteinEditDistance(double t) {
        threshold = t;
    }

    @Override
    public double compare(
            Event record1,
            Event record2,
            Correspondence<DefaultSchemaElement, Event> schemaCorrespondences) {
        return bestListSimilarity.getBestEditDistance(sim, record1.getUris(), record2.getUris(), threshold);
    }

}





