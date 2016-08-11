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
package com.dnastack.beacon.adapter.impl;


import com.dnastack.beacon.adapter.api.BeaconAdapter;
import com.dnastack.beacon.adapter.vcf.VcfBeacon;
import com.dnastack.beacon.exceptions.BeaconException;
import com.dnastack.beacon.utils.AdapterConfig;
import com.dnastack.beacon.utils.ConfigValue;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.NonNull;
import org.ga4gh.beacon.Beacon;
import org.ga4gh.beacon.BeaconAlleleRequest;
import org.ga4gh.beacon.BeaconAlleleResponse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/**
 * VcfBeaconAdapter
 * <p>
 * This adapter "beaconizes" one or more zipped and indexed VCF files allowing them to be queried in the same manner
 * as any other beacon. Each vcf file is a single dataset (corresponding to a dataset listed in the beacon definition).
 * The VcfBeaconAdapter is configured using the {@link AdapterConfig} class and expects there to be several defined
 * config parameters present
 * <p>
 * Required Config Values:
 * 1. Name: "filenames", Value: "list of comma seperate filenames / paths to vcf files" ex: "/path/to/file1,/path/to/file2"
 * - These files must be bgzipped and indexed with tabix in order to be opened by the adapter
 * - They must be valid VCF 4.0 files
 * <p>
 * One Of the following:
 * 2. Name: "beaconJsonFile", Value: "path to a json representation of a beacon" {@link Beacon}
 * - The json file should contain a full representation of a beacon
 * - The number of datasets MUST equal the number of vcf file
 * - The datasets and vcf files are linked in order, ie the first file listed is interpreted as the first Dataset listed
 * <p>
 * 3. Name: "beaconJson", Value: "Beacon json representation"
 * - The number of datasets MUST equal the number of vcf file
 * - The datasets and vcf files are linked in order, ie the first file listed is interpreted as the first Dataset listed
 * <p>
 * <p>
 * <p>
 * To create a new instance of the VcfBeaconAdapter simply call the no Arguments constructor. Before the beacon can be used
 * however, it must be configured. Create your configuration object ({@link AdapterConfig}) and then call the
 * {@link VcfBeaconAdapter#initAdapter(AdapterConfig)} method. Once the adapter has been configured it is now
 * ready to use
 * <p>
 * Beacon Version: 0.3
 *
 * @author patmagee patrickmageee@gmail.com
 */
public class VcfBeaconAdapter implements BeaconAdapter {

    private VcfBeacon vcfBeacon;

    /**
     * Initialize the adapter for a VcfBeacon. The adapter expects several config parameters tro be present in the
     * adapterConfig object
     * <p>
     * 1. filenames: Comma Seperated list of filenames pointing to vcf files. VCF files must be in bgzipped format and indexed accordingly
     * 2. beaconJsonFile: json file describing the meta data for this beacon. The file must be a JSON representation of the org.ga4g.beacon.Beacon class
     * 3. beaconJson: json string describing the meta data for this beacon
     *
     * @param adapterConfig config object that tells the adapter how to be configured
     */
    @Override
    public void initAdapter(AdapterConfig adapterConfig) {
        List<ConfigValue> configValues = adapterConfig.getConfigValues();
        readRequiredParamsFromConfig(configValues);
    }

    private Beacon readBeaconJsonFile(String filename) {
        File beaconJsonFile = new File(filename);
        if (!beaconJsonFile.exists()) {
            throw new RuntimeException("BeaconJson file does not exist");
        }
        try {

            String beaconJson = new String(Files.readAllBytes(beaconJsonFile.toPath()));
            return readBeaconJson(beaconJson);

        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private Beacon readBeaconJson(String json) {
        Gson gson = new GsonBuilder().create();
        return gson.fromJson(json, Beacon.class);
    }

    /**
     * Cycle through the configValues and extract all of the required parameters. In order to properly configure
     * the vcf files, a comma seperated list of vcf files and a single path to json fike describing the beacon must
     * be supplied. If all of the parameters are supplioed a VcfBeacon will attempt to be created
     *
     * @param configValues values to use for configuration
     */
    private void readRequiredParamsFromConfig(@NonNull List<ConfigValue> configValues) {
        //Expected required parameters
        String[] filenames = null;
        Beacon beacon = null;

        for (ConfigValue configValue : configValues) {
            switch (configValue.getName()) {
                case "filenames":
                    String names = configValue.getValue();
                    if (names.length() == 0) {
                        throw new RuntimeException("No File names specified");
                    }
                    filenames = names.split(",");
                    break;
                case "beaconJsonFile":
                    beacon = readBeaconJsonFile(configValue.getValue());
                    break;
                case "beaconJson":
                    beacon = readBeaconJson(configValue.getValue());
            }
        }

        if (filenames == null) {
            throw new RuntimeException("Missing required parameter: filenames. Please supply a comma separated list of files to load");
        }
        if (beacon == null) {
            throw new RuntimeException("Missing required parameter: beaconJson. Please add the appropriate configuration paramter then retry");
        }

        vcfBeacon = new VcfBeacon(beacon, filenames);
    }

    @Override
    public BeaconAlleleResponse getBeaconAlleleResponse(BeaconAlleleRequest beaconAlleleRequest) throws BeaconException {
        checkAdapterInit();
        return vcfBeacon.search(beaconAlleleRequest);
    }

    @Override
    public BeaconAlleleResponse getBeaconAlleleResponse(String referenceName, Long start, String referenceBases, String alternateBases, String assemblyId, List<String> datasetIds, Boolean includeDatasetResponses) throws BeaconException {
        checkAdapterInit();
        return vcfBeacon.search(referenceName, start, referenceBases, alternateBases, assemblyId, datasetIds, includeDatasetResponses);
    }

    @Override
    public Beacon getBeacon() throws BeaconException {
        checkAdapterInit();
        return vcfBeacon.getBeacon();
    }

    private void checkAdapterInit() {
        if (vcfBeacon == null) {
            throw new IllegalStateException("VcfBeaconAdapter adapter has not been initialized and does not point to any vcf file");
        }
    }
}
