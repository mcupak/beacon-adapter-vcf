/*
 * The MIT License
 *
 * Copyright 2014 Patrick Magee (patrickmageee@gmail.com).
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.dnastack.beacon.adapter.vcf;

import lombok.Getter;
import lombok.NonNull;
import org.ga4gh.beacon.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * VcfBeacon
 * <p>
 * This class wraps a set of VCF files in a single beacon, it allows them to be queried and form all of the appropriate
 * Beacon level responses. Each single VCF is considered as a single dataset {@link VcfDataset}, therefore the Beacon must list the same
 * number datasets as vcf files you wish to beaconize.
 * <p>
 * All vcf files must be bgzipped and tabixed.
 *
 * @author patmagee patrickmageee@gmail.com
 * @author Miro Cupak (mirocupak@gmail.com)
 */
public class VcfBeacon {

    @Getter
    private final Beacon beacon;
    private final Map<String, VcfDataset> datasets;

    /**
     * VcfBeacon
     * <p>
     * Constructor for forming a new VcfBeacon. Takes a single Beacon object and a list of filenames pointing to valid
     * vcf files.
     * <p>
     * THe number of datasets must equal the number of vcf files. Additionally, the datasets and the files will be used
     * in a 1:1 fashion. ie the first dataset corresponds to the first file in the list.
     *
     * @param beacon    Beacon object
     * @param filenames list of vcf filenames
     */
    public VcfBeacon(@NonNull Beacon beacon, @NonNull String[] filenames) {
        this.beacon = beacon;
        Map<String, VcfDataset> datasetMap = new HashMap<>();

        if (beacon.getDatasets() == null) {
            throw new RuntimeException("A list of the included datasets is required in the beacon.json file");
        }

        if (filenames.length != beacon.getDatasets().size()) {
            throw new RuntimeException(
                    "Length of DatasetIds and Filenames does not match. Each file constitutes a single dataset");
        }

        for (int i = 0; i < filenames.length; i++) {
            BeaconDataset dataset = beacon.getDatasets().get(i);
            String filename = filenames[i];
            datasetMap.put(dataset.getId(), new VcfDataset(dataset, filename));
        }

        this.datasets = Collections.unmodifiableMap(datasetMap);
    }

    /**
     * SearchDataset
     * <p>
     * Search a single dataset for the existence of a variant based on the parameters defined in the {@link BeaconAlleleRequest}
     * and create a single {@link BeaconDatasetAlleleResponse} object. The returned object should minimally indicate whether or not
     * the beacon was found, (or if an error was encountered)
     *
     * @param datasetId id of the dataset to search
     * @param request   request object
     * @return dataset response
     */
    private BeaconDatasetAlleleResponse searchDataset(String datasetId, BeaconAlleleRequest request) {
        BeaconDatasetAlleleResponse response;
        VcfDataset dataset = datasets.get(datasetId);
        if (dataset == null) {
            response = new BeaconDatasetAlleleResponse();
            BeaconError error = new BeaconError();
            error.setErrorCode(404);
            error.setErrorMessage("Could not find dataset with id: " + datasetId);
            response.setError(error);
        } else {
            response = dataset.search(request);
        }
        return response;
    }

    /**
     * finalizeReponse
     * <p>
     * Form the final {@link BeaconAlleleResponse} object to send back to the user, based on the response from each
     * of the datasets.
     * <p>
     * If a single dataset was queried and an error was encountered in that dataset, the error should also be shown at the
     * level of the main response and accessible through {@link BeaconAlleleResponse#getError()}. In this isntance exists
     * should also be set to null in all cases.
     * <p>
     * In the case where multiple datassets are defined, an error should not propogate to the top level response. Instead
     * each dataset should define whether an error was encountered. the Top level response should simply indicate whether
     * a variant exists or not.
     *
     * @param request  Initial request
     * @param response Partially populated response object
     * @param datasets Datasets to include
     * @return Fully populated response object
     */
    private BeaconAlleleResponse finalizeResponse(BeaconAlleleRequest request, BeaconAlleleResponse response, List<BeaconDatasetAlleleResponse> datasets) {

        if (datasets.size() == 1 && datasets.get(0).getError() != null) {
            response.setError(datasets.get(0).getError());
            response.setExists(null);
        } else {
            response.setExists(datasets.stream().anyMatch(BeaconDatasetAlleleResponse::isExists));
        }

        if (request.getIncludeDatasetResponses() == BeaconAlleleRequest.IncludeDatasetResponsesEnum.ALL) {
            response.setDatasetAlleleResponses(datasets);
        }

        // TODO: add behavior for HIT and MISS

        return response;
    }

    /**
     * Simple method for creating an empty response object
     *
     * @param request Initial request
     * @return response
     */
    private BeaconAlleleResponse createResponse(BeaconAlleleRequest request) {
        BeaconAlleleResponse response = new BeaconAlleleResponse();
        response.setAlleleRequest(request);
        response.setBeaconId(beacon.getId());

        return response;
    }

    /**
     * Search
     * <p>
     * Search a ${@link VcfDataset} for a vairnt based on the search criteria the user has defined. Search each dataset defined in the DatasetIds and
     * aggregate the results into a single ${@link BeaconAlleleResponse} object indicating whether the variant was found in any of the
     * datasets or if an error was encountered.
     *
     * @param referenceName           name of contig
     * @param start                   start position
     * @param referenceBases          reference bases to search
     * @param alternateBases          alternate bases to search
     * @param assemblyId              genome assembly Id
     * @param datasetIds              list of datasets to search
     * @param includeDatasetResponses shoudl the dataset responses be included in the final response object
     * @return Beacon Allele Response with existence of variant
     */
    public BeaconAlleleResponse search(Chromosome referenceName, Long start, String referenceBases, String alternateBases, String assemblyId, List<String> datasetIds, BeaconAlleleRequest.IncludeDatasetResponsesEnum includeDatasetResponses) {

        BeaconError error = null;
        if (referenceName == null) {
            error = new BeaconError();
            error.setErrorMessage("Reference name cannot be null");
        } else if (start == null || start < 0) {
            error = new BeaconError();
            error.setErrorMessage("Start cannot be null or less then 0");
        } else if (referenceBases == null) {
            error = new BeaconError();
            error.setErrorMessage("Reference bases cannot be null");
        } else if (alternateBases == null) {
            error = new BeaconError();
            error.setErrorMessage("Alternate bases cannot be null");
        } else if (assemblyId == null) {
            error = new BeaconError();
            error.setErrorMessage("Assembly Id cannot be null");
        } else if (datasetIds == null || datasetIds.size() == 0) {
            error = new BeaconError();
            error.setErrorMessage("DatasetIds cannot be null and must include at lesat 1 id");
        }

        if (error != null) {
            error.setErrorCode(400);
            BeaconAlleleResponse response = createResponse(null);
            response.setError(error);
            return response;
        }

        if (includeDatasetResponses == null) {
            includeDatasetResponses = BeaconAlleleRequest.IncludeDatasetResponsesEnum.NONE;
        }

        BeaconAlleleRequest request = new BeaconAlleleRequest();
        request.setReferenceName(referenceName);
        request.setStart(start);
        request.setReferenceBases(referenceBases);
        request.setAlternateBases(alternateBases);
        request.setAssemblyId(assemblyId);
        request.setDatasetIds(datasetIds);
        request.setIncludeDatasetResponses(includeDatasetResponses);
        return search(request);
    }

    /**
     * Search
     * <p>
     * Search a ${@link VcfDataset} for a vairnt based on the search criteria the user has defined. Search each dataset defined in the DatasetIds and
     * aggregate the results into a single ${@link BeaconAlleleResponse} object indicating whether the variant was found in any of the
     * datasets or if an error was encountered.
     *
     * @param request request object
     * @return
     */
    public BeaconAlleleResponse search(BeaconAlleleRequest request) {
        if (request.getIncludeDatasetResponses() == null) {
            request.setIncludeDatasetResponses(BeaconAlleleRequest.IncludeDatasetResponsesEnum.NONE);
        }
        BeaconAlleleResponse response = createResponse(request);

        if (request.getDatasetIds().size() == 0) {
            BeaconError error = new BeaconError();
            error.setErrorCode(500);
            error.setErrorMessage("No datasets defined. At least one dataset must be defined");
            response.setError(error);
            return response;
        }
        List<BeaconDatasetAlleleResponse> datasetRespones = request.getDatasetIds()
                                                                   .stream()
                                                                   .map(datasetId -> searchDataset(datasetId, request))
                                                                   .collect(Collectors.toList());

        return finalizeResponse(request, response, datasetRespones);
    }

}
