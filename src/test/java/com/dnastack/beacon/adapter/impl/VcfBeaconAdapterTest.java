package com.dnastack.beacon.adapter.impl;

import com.dnastack.beacon.adapter.api.BeaconAdapter;
import com.dnastack.beacon.exceptions.BeaconException;
import com.dnastack.beacon.utils.AdapterConfig;
import com.dnastack.beacon.utils.ConfigValue;
import org.ga4gh.beacon.*;
import org.junit.Test;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Adapter test.
 *
 * @author patmagee patrickmageee@gmail.com
 * @author Miro Cupak (mirocupak@gmail.com)
 */
public class VcfBeaconAdapterTest {

    private final static String NO_GT_FILE = "test_no_genotype.vcf.gz";
    private final static String GT_FILE = "test.vcf.gz";
    private final static String GT_FILE_NO_INDEX = "test_no_index.vcf.gz";
    private final static String GT_FILE_SV = "test_sv.vcf.gz";
    private final static String BEACON_FILE = "test_beacon.json";
    private final static AdapterConfig adapterConfig = createConfig();

    private static AdapterConfig createConfig() {
        ClassLoader cl = VcfBeaconAdapterTest.class.getClassLoader();
        try {
            String testGtVcf = cl.getResource(GT_FILE).toURI().getPath();
            String testNoGtVcf = cl.getResource(NO_GT_FILE).toURI().getPath();
            String testGtSvVcf = cl.getResource(GT_FILE_SV).toURI().getPath();
            String beaconJson = cl.getResource(BEACON_FILE).toURI().getPath();
            List<ConfigValue> values = new ArrayList<>();

            values.add(ConfigValue.builder().name("filenames").value(String.format("%s,%s,%s", testGtVcf, testNoGtVcf, testGtSvVcf)).build());
            values.add(ConfigValue.builder().name("beaconJsonFile").value(beaconJson).build());

            return AdapterConfig.builder().name("vcf_test_beacon")
                    .adapterClass(AdapterConfig.class.getCanonicalName()).configValues(values).build();
        } catch (URISyntaxException e) {
            throw new NullPointerException(e.getMessage());
        }

    }

    private static AdapterConfig createConfig(String... filenames) {
        ClassLoader cl = VcfBeaconAdapterTest.class.getClassLoader();
        try {
            String beaconJson = cl.getResource(BEACON_FILE).toURI().getPath();
            List<ConfigValue> values = new ArrayList<>();

            String files = String.join(",", filenames);

            values.add(ConfigValue.builder().name("filenames").value(files).build());
            values.add(ConfigValue.builder().name("beaconJsonFile").value(beaconJson).build());

            return AdapterConfig.builder().name("vcf_test_beacon")
                    .adapterClass(AdapterConfig.class.getCanonicalName()).configValues(values).build();
        } catch (URISyntaxException e) {
            throw new NullPointerException(e.getMessage());
        }

    }

    @Test
    public void testInitAdapter() throws BeaconException {
        BeaconAdapter adapter = new VcfBeaconAdapter();
        adapter.initAdapter(adapterConfig);
        assertThat(adapter.getBeacon()).isNotNull();
    }

    @Test
    public void testAdapterMustBeInitialized() {
        BeaconAdapter adapter = new VcfBeaconAdapter();
        assertThatThrownBy(adapter::getBeacon).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> adapter.getBeaconAlleleResponse(null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> adapter.getBeaconAlleleResponse(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testInitAdapterMissingParams() {

        AdapterConfig missingBeaconJson = AdapterConfig.builder().name(adapterConfig.getName())
                .adapterClass(adapterConfig.getAdapterClass())
                .configValues(adapterConfig.getConfigValues()
                        .stream()
                        .filter(x -> !x.getName().equals("beaconJsonFile"))
                        .collect(Collectors.toList()))
                .build();

        AdapterConfig missingFilename = AdapterConfig.builder().name(adapterConfig.getName())
                .adapterClass(adapterConfig.getAdapterClass())
                .configValues(adapterConfig.getConfigValues()
                        .stream()
                        .filter(x -> !x.getName().equals("filenames"))
                        .collect(Collectors.toList()))
                .build();

        AdapterConfig missingBoth = AdapterConfig.builder().name(adapterConfig.getName())
                .adapterClass(adapterConfig.getAdapterClass()).configValues(new ArrayList<>()).build();

        BeaconAdapter adapter = new VcfBeaconAdapter();

        assertThatThrownBy(() -> adapter.initAdapter(missingBeaconJson)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> adapter.initAdapter(missingFilename)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> adapter.initAdapter(missingBoth)).isInstanceOf(RuntimeException.class);

    }

    @Test
    public void testInvalidParameters() {

    }

    @Test
    public void testMissingIndexFile() throws URISyntaxException {

        AdapterConfig config = createConfig(getClass().getClassLoader().getResource(GT_FILE).toURI().getPath(),
                getClass().getClassLoader().getResource(GT_FILE_SV).toURI().getPath(),
                getClass().getClassLoader().getResource(GT_FILE_NO_INDEX).toURI().getPath());
        BeaconAdapter adapter = new VcfBeaconAdapter();
        assertThatThrownBy(() -> adapter.initAdapter(config)).isInstanceOf(RuntimeException.class)
                .hasMessage(
                        "VCF File requires index file, but it does not exist");
    }

    @Test
    public void testNonexistantDataset() throws BeaconException {
        BeaconAdapter adapter = new VcfBeaconAdapter();
        adapter.initAdapter(adapterConfig);

        BeaconAlleleRequest request = adapter.getBeacon().getSampleAlleleRequests().get(0);
        List<String> datasets = request.getDatasetIds();
        datasets.add("NON_EXISTANT_DATASET");
        request.setDatasetIds(datasets);

        //This should return two datasets, one of them shows that it exists and the other shows that it doesnt and it has
        //an error
        BeaconAlleleResponse response = adapter.getBeaconAlleleResponse(request);
        assertThat(response.getDatasetAlleleResponses()).hasSize(2);
        assertThat(response.getError()).isNull();
        assertThat(response.isExists()).isTrue();
        assertThat(response.getDatasetAlleleResponses().stream().anyMatch(x -> x.getError() != null)).isTrue();
        assertThat(response.getDatasetAlleleResponses()
                .stream()
                .filter(x -> x.getError() != null)
                .count()).isGreaterThan(0);

        //In the instancae where a single dataset searched and an error occurs, the error should be propagated to the top level object
        request.setDatasetIds(Collections.singletonList("NON_EXISTANT_DATASET"));
        response = adapter.getBeaconAlleleResponse(request);
        assertThat(response.getDatasetAlleleResponses()).hasSize(1);
        assertThat(response.getError()).isNotNull();
        assertThat(response.getError().getErrorCode()).isEqualTo(404);
        assertThat(response.isExists()).isNull();
        assertThat(response.getDatasetAlleleResponses().stream().allMatch(x -> x.getError() != null)).isTrue();

    }

    @Test
    public void testQuery() throws BeaconException {
        BeaconAdapter adapter = new VcfBeaconAdapter();
        adapter.initAdapter(adapterConfig);

        Beacon beacon = adapter.getBeacon();

        for (BeaconAlleleRequest request : beacon.getSampleAlleleRequests()) {
            BeaconAlleleResponse response = adapter.getBeaconAlleleResponse(request);
            assertThat(response.getError()).isNull();
            assertThat(response.isExists()).isTrue();

            BeaconAlleleResponse response2 = adapter.getBeaconAlleleResponse(request.getReferenceName(),
                    request.getStart(),
                    request.getStartMin(),
                    request.getStartMax(),
                    request.getEnd(),
                    request.getEndMin(),
                    request.getEndMax(),
                    request.getReferenceBases(),
                    request.getAlternateBases(),
                    request.getVariantType(),
                    request.getAssemblyId(),
                    request.getDatasetIds(),
                    request.getIncludeDatasetResponses());

            assertThat(response2.getError()).isNull();
            assertThat(response2.isExists()).isTrue();

            assertThat(response).isEqualToComparingFieldByField(response2);
        }

    }

    @Test
    public void testQueryWithNotFoundVariant() throws BeaconException {
        BeaconAdapter adapter = new VcfBeaconAdapter();
        adapter.initAdapter(adapterConfig);

        Beacon beacon = adapter.getBeacon();

        BeaconAlleleRequest request = beacon.getSampleAlleleRequests().get(0);
        List<String> datasets = request.getDatasetIds();
        datasets.add(beacon.getDatasets().get(1).getId());
        request.setDatasetIds(datasets);

        BeaconAlleleResponse response = adapter.getBeaconAlleleResponse(request);

        assertThat(response.getError()).isNull();
        assertThat(response.isExists()).isTrue();
        assertThat(response.getDatasetAlleleResponses().stream().filter(BeaconDatasetAlleleResponse::isExists).count()).isEqualTo(1);
        assertThat(response.getDatasetAlleleResponses().stream().filter(x -> !x.isExists()).count()).isEqualTo(1);
        assertThat(response.getDatasetAlleleResponses().stream().allMatch(x -> x.getError() == null)).isTrue();

    }


    @Test
    public void testIncludeDatasetResponsesEnum() throws BeaconException {
        BeaconAdapter adapter = new VcfBeaconAdapter();
        adapter.initAdapter(adapterConfig);

        BeaconAlleleRequest request = new BeaconAlleleRequest();
        request.setReferenceName(Chromosome._20);
        request.setStart(76962L);
        request.setReferenceBases("T");
        request.setAlternateBases("C");
        request.setDatasetIds(null); // means that all datasets must be queried
        request.setAssemblyId("grch37");

        BeaconAlleleResponse response;

        //Missing value must be evaluated as NONE
        request.setIncludeDatasetResponses(null);
        response = adapter.getBeaconAlleleResponse(request);
        assertThat(response.getAlleleRequest().getIncludeDatasetResponses()).isEqualTo(BeaconAlleleRequest.IncludeDatasetResponsesEnum.NONE);
        assertThat(response.isExists()).isTrue();
        assertThat(response.getDatasetAlleleResponses()).isNull();

        //NONE must return null
        request.setIncludeDatasetResponses(BeaconAlleleRequest.IncludeDatasetResponsesEnum.NONE);
        response = adapter.getBeaconAlleleResponse(request);
        assertThat(response.isExists()).isTrue();
        assertThat(response.getDatasetAlleleResponses()).isNull();

        //ALL must return all datasets
        request.setIncludeDatasetResponses(BeaconAlleleRequest.IncludeDatasetResponsesEnum.ALL);
        response = adapter.getBeaconAlleleResponse(request);
        assertThat(response.getDatasetAlleleResponses().size()).isEqualTo(3);
        // at least one of tree datasets has the queried variant
        assertThat(response.isExists()).isTrue();
        assertThat(response.getDatasetAlleleResponses().stream()
                .filter(response1 -> response1.isExists() != null)
                .filter(BeaconDatasetAlleleResponse::isExists)
                .count())
                .isEqualTo(1);

        //HIT must return only datasets with queried variant
        request.setIncludeDatasetResponses(BeaconAlleleRequest.IncludeDatasetResponsesEnum.HIT);
        response = adapter.getBeaconAlleleResponse(request);
        assertThat(response.isExists()).isTrue();
        assertThat(response.getDatasetAlleleResponses().size()).isEqualTo(1);

        //MISS must return only datasets that don't have the queried variant
        //Missing results (wrong Assembly ID for example) aren't treated as MISS
        request.setIncludeDatasetResponses(BeaconAlleleRequest.IncludeDatasetResponsesEnum.MISS);
        response = adapter.getBeaconAlleleResponse(request);
        assertThat(response.isExists()).isTrue();
        assertThat(response.getDatasetAlleleResponses().size()).isEqualTo(1);
    }

}