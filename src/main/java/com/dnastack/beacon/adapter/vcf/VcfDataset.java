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

import htsjdk.samtools.util.CloseableIterator;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import lombok.NonNull;
import org.ga4gh.beacon.*;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * VcfDataset
 * <p>
 * This class wraps a single VCF file, allowing for direct read access and querying from the file. The file is essentially
 * turned into a beacon that can be easily queryied.
 * <p>
 * The Vcf file must be bgzipped and have an associated tabixed file to allow for random read access. Each file is considered
 * a single dataset, and may or may not contain Genotype information. If the file has no genotyping information a recorded
 * Alt call is interpreted as a hit. If the File has Genotype inforamtion associated with it, Both the ref / alts have to match
 * but additionally, at least one sample must have alternate allele that is being searched for
 *
 * @author patmagee patrickmageee@gmail.com
 * @author Miro Cupak (mirocupak@gmail.com)
 */
class VcfDataset {

    private final VCFFileReader reader;
    private final VCFHeader header;
    private final BeaconDataset dataset;

    /**
     * Simple matcher for determining if a genotype has a specific allele
     *
     * @param allele Allele to test
     * @param gt     genotype to test against
     * @return boolean
     */
    private static boolean matchGenotypeString(String allele, String gt) {
        String[] gts = gt.split("[|/]");

        for (String altGt : gts) {
            if (altGt.equals(allele)) {
                return true;
            }
        }
        return false;
    }

    /**
     * VcfDataset
     * <p>
     * Constructor for creating a single VcfDataset based on one Datset object and one Vcf File
     * <p>
     * The Vcf File must be bgzipped file with an associated tabixed file. If your vcf file is located at "/tmp/test.vcf.gz",
     * then the constructor will look for an index file at "/tmp/test.vcf.gz.tbi". This file must be present
     *
     * @param dataset  dataset object for this vcf file
     * @param filename file name of vcf file
     */
    public VcfDataset(@NonNull BeaconDataset dataset, @NonNull String filename) {

        File vcfFile = new File(filename);
        File idxFile = new File(filename + ".tbi");

        if (!idxFile.exists()) {
            throw new RuntimeException("VCF File requires index file, but it does not exist");
        } else if (!vcfFile.exists()) {
            throw new RuntimeException("Vcf File not found");
        }

        this.reader = new VCFFileReader(vcfFile, idxFile, true);
        this.header = new VCFHeader(reader.getFileHeader());
        this.dataset = dataset;

    }

    /**
     * Search
     * <p>
     * Search the Vcf file for a variant that matches the BeaconAlleleRequest.
     * 1. First get all of the variants recordered from region computed by: request.start -> request.start + max(len(ref), len(alt))
     * 2. Cycle over the variants and select only the ones that contain the appropriate alt and ref alleles
     * 3. If there is no samples / gt info, set the exists parameter to true and return
     * 4. If GT info is available, cycle throught the samples, and determine if any have the alternate allele.
     * 5. Set the exists parameter accordingly, and compute the callCount, variantCount, sampleCount and frequency
     *
     * @param request initial variant request
     * @return search results
     */
    public BeaconDatasetAlleleResponse search(BeaconAlleleRequest request) {

        Allele ref = Allele.create(request.getReferenceBases(), true);
        Allele alt = Allele.create(request.getAlternateBases(), false);

        BeaconDatasetAlleleResponse response = new BeaconDatasetAlleleResponse();
        response.setDatasetId(dataset.getId());

        //Ensure the request assembly matches the assembly for this dataset
        if (!request.getAssemblyId().equals(dataset.getAssemblyId())) {
            BeaconError error = new BeaconError();
            error.setErrorCode(400);
            error.setErrorMessage("Invalid Assembly");
            response.setError(error);
            return response;
        }

        //Set the intial exists status
        response.setExists(false);

        //determine the offest for the end
        int offset = request.getAlternateBases().length() > request.getReferenceBases().length()
                     ? request.getAlternateBases().length()
                     : request.getAlternateBases().length();

        CloseableIterator<VariantContext> context = reader.query(request.getReferenceName().toString(),
                                                                 request.getStart().intValue(),
                                                                 request.getStart().intValue() + offset);

        long variantCount = 0;
        long callCount = 0;
        long sampleCount = 0;
        int count = 0;

        //Cycle over the results
        while (context.hasNext()) {
            VariantContext variantContext = context.next();
            count++;
            //For each rresult from the query, determine if the ref / alt allele match the search criteria
            if (variantContext.getReference().basesMatch(ref) && variantContext.getAlternateAlleles()
                                                                               .stream()
                                                                               .anyMatch(a -> a.basesMatch(alt))) {

                //If there is no genotyping data it is enough that the allele shows in the Alt Column
                if (!header.hasGenotypingData()) {
                    response.setExists(true);
                    variantCount++;
                    callCount++;
                    //Do any of the samples contain the variant
                } else {
                    Long numMatches = variantContext.getGenotypes()
                                                    .stream()
                                                    .map(g -> g.getGenotypeString())
                                                    .filter(s -> matchGenotypeString(request.getAlternateBases(), s))
                                                    .count();

                    if (numMatches > 0) {
                        response.setExists(true);
                        variantCount++;
                        callCount++;
                        sampleCount += numMatches;
                    }
                }
            }
        }
        if (response.isExists()) {
            response.setVariantCount(variantCount);
            response.setCallCount(callCount);
            if (header.hasGenotypingData()) {
                response.setSampleCount(sampleCount);
                response.setFrequency(BigDecimal.valueOf(sampleCount).divide(BigDecimal.valueOf(header.getNGenotypeSamples())));
            }

        }

        if (count > 1) {
            KeyValuePair keyValuePair = new KeyValuePair();
            keyValuePair.setKey("warn");
            keyValuePair.setValue("Multiple variants were found with the same query");
            List<KeyValuePair> info = new ArrayList<>();
            info.add(keyValuePair);
            response.setInfo(info);
        }

        return response;
    }

}
