/**
 *
 */
package au.org.ala.sds.validation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import au.org.ala.sds.model.SensitiveTaxon;
import au.org.ala.sds.model.SensitivityInstance;
import au.org.ala.sds.model.SensitivityZone;
import au.org.ala.sds.util.GeneralisedLocation;
import au.org.ala.sds.util.GeneralisedLocationFactory;
import au.org.ala.sds.util.ValidationUtils;

/**
 *
 * @author Peter Flemming (peter.flemming@csiro.au)
 */
public class ConservationService implements ValidationService {

    private ReportFactory reportFactory;
    private final SensitiveTaxon taxon;

    public ConservationService(SensitiveTaxon taxon, ReportFactory reportFactory) {
        super();
        this.reportFactory = reportFactory;
        this.taxon = taxon;
    }

    /**
     * @param taxon
     * @param facts
     * @return
     */
    public ValidationOutcome validate(Map<String, String> biocacheData) {
        FactCollection facts = new FactCollection(biocacheData);
        ValidationReport report = reportFactory.createValidationReport(taxon);

        // Validate location
        if (!ValidationUtils.validateLocationCoords(facts, report)) {
            return new ValidationOutcome(report, false);
        }
        if (!ValidationUtils.validateLocation(facts, report)) {
            return new ValidationOutcome(report, false);
        }

        // Generalise location
        List<SensitivityZone> zones = SensitivityZone.getListFromString(facts.get(FactCollection.ZONES_KEY));
        String latitude = facts.get(FactCollection.DECIMAL_LATITUDE_KEY);
        String longitude = facts.get(FactCollection.DECIMAL_LONGITUDE_KEY);

        GeneralisedLocation gl = GeneralisedLocationFactory.getGeneralisedLocation(latitude, longitude, taxon, zones);
        ValidationOutcome outcome = new ValidationOutcome(report);

        // Assemble result map
        Map<String, Object> results = new HashMap<String, Object>();
        Map<String, String> originalSensitiveValues = new HashMap<String, String>();
        if (gl.isSensitive()) {
            StringBuilder extra = new StringBuilder();
            for(SensitivityInstance si :gl.getSensitivityInstances()){
                if(extra.length()>0)
                    extra.append("\t");
                else
                    extra.append("\n");
                extra.append("Sensitive in ")
                      .append(si.getZone())
                      .append(" [")
                      .append(si.getCategory().getValue())
                      .append(", ").append(si.getAuthority()).append("]");
            }
            String extraDesc = extra.toString();
            outcome.setSensitive(true);
            results.put(FactCollection.DECIMAL_LATITUDE_KEY, gl.getGeneralisedLatitude());
            results.put(FactCollection.DECIMAL_LONGITUDE_KEY, gl.getGeneralisedLongitude());
            results.put("generalisationInMetres", gl.getGeneralisationInMetres());
            results.put("dataGeneralizations", gl.getDescription() + extraDesc);
            emptyValueIfNecessary("locationRemarks", biocacheData, originalSensitiveValues, results);
            emptyValueIfNecessary("verbatimLatitude", biocacheData, originalSensitiveValues, results);
            emptyValueIfNecessary("verbatimLongitude", biocacheData, originalSensitiveValues, results);
            if (gl.getGeneralisationInMetres().equals("") && gl.getGeneralisedLatitude().equals("")) {
                results.put("informationWithheld", "The location has been withheld in accordance with " + facts.get(FactCollection.STATE_PROVINCE_KEY) + " sensitive species policy");
            }
            originalSensitiveValues.put(FactCollection.DECIMAL_LATITUDE_KEY, gl.getOriginalLatitude());
            originalSensitiveValues.put(FactCollection.DECIMAL_LONGITUDE_KEY, gl.getOriginalLongitude());
        } else {
            outcome.setSensitive(false);
        }

        // Handle Birds Australia occurrences
        if (facts.get("dataResourceUid") != null && facts.get("dataResourceUid").equalsIgnoreCase("dr359")) {
//            results.put("eventID", "");
//            results.put("locationRemarks", "");
//            results.put("day", "");
            emptyValueIfNecessary("eventID", biocacheData, originalSensitiveValues, results);
            emptyValueIfNecessary("day", biocacheData, originalSensitiveValues, results);
            emptyValueIfNecessary("eventDate",biocacheData, originalSensitiveValues, results);
            results.put("informationWithheld", "The eventID and day information has been withheld in accordance with Birds Australia data policy");
//            originalSensitiveValues.put("eventID", facts.get("eventID"));
//            originalSensitiveValues.put("locationRemarks", facts.get("locationRemarks"));
//            originalSensitiveValues.put("day", facts.get("day"));
        }

        results.put("originalSensitiveValues", originalSensitiveValues);
        outcome.setResult(results);
        outcome.setValid(true);

        return outcome;
    }
    private void emptyValueIfNecessary(String field, Map<String,String> facts, Map<String,String> originalSensitiveValues, Map<String,Object> results){
        if(facts.containsKey(field)){
            results.put(field, "");
            originalSensitiveValues.put(field, facts.get(field));
        }
    }

    public void setReportFactory(ReportFactory reportFactory) {
        this.reportFactory = reportFactory;
    }

}
