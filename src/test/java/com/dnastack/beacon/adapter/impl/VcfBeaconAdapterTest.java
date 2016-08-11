package com.dnastack.beacon.adapter.impl;

import com.dnastack.beacon.adapter.api.BeaconAdapter;
import com.dnastack.beacon.exceptions.BeaconException;
import com.dnastack.beacon.utils.AdapterConfig;
import com.dnastack.beacon.utils.ConfigValue;
import org.apache.commons.lang.StringUtils;
import org.ga4gh.beacon.Beacon;
import org.ga4gh.beacon.BeaconAlleleRequest;
import org.ga4gh.beacon.BeaconAlleleResponse;
import org.junit.Test;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/**
 * Created by patrickmagee on 2016-08-11.
 */
public class VcfBeaconAdapterTest {

    private final static String NO_GT_FILE = "test_no_genotype.vcf.gz";
    private final static String GT_FILE = "test.vcf.gz";
    private final static String GT_FILE_NO_INDEX = "test_no_index.vcf.gz";
    private final static String BEACON_FILE = "test_beacon.json";
    private final static AdapterConfig adapterConfig = createConfig();


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
        assertThatThrownBy(() -> adapter.getBeaconAlleleResponse(null, null, null, null, null, null, null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> adapter.getBeaconAlleleResponse(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testInitAdapterMissingParams() {

        AdapterConfig missingBeaconJson = new AdapterConfig(adapterConfig.getName(), adapterConfig.getAdapterClass(), adapterConfig
                .getConfigValues()
                .stream()
                .filter(x -> !x.getName().equals("beaconJsonFile"))
                .collect(Collectors.toList()));
        AdapterConfig missingFilename = new AdapterConfig(adapterConfig.getName(), adapterConfig.getAdapterClass(), adapterConfig
                .getConfigValues()
                .stream()
                .filter(x -> !x.getName().equals("filenames"))
                .collect(Collectors.toList()));

        AdapterConfig missingBoth = new AdapterConfig(adapterConfig.getName(), adapterConfig.getAdapterClass(), new ArrayList<ConfigValue>());

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

        AdapterConfig config = createConfig(getClass()
                .getClassLoader()
                .getResource(GT_FILE)
                .toURI()
                .getPath(), getClass().getClassLoader().getResource(GT_FILE_NO_INDEX).toURI().getPath());
        BeaconAdapter adapter = new VcfBeaconAdapter();
        assertThatThrownBy(() -> adapter.initAdapter(config))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("VCF File requires index file, but it does not exist");
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
        assertThat(response.getExists()).isTrue();
        assertThat(response.getDatasetAlleleResponses().stream().anyMatch(x-> x.getError() != null)).isTrue();
        assertThat(response.getDatasetAlleleResponses().stream().filter(x -> x.getError() != null ).count()).isGreaterThan(0);

        //In the instancae where a single dataset searched and an error occurs, the error should be propagated to the top level object
        request.setDatasetIds(Arrays.asList("NON_EXISTANT_DATASET"));
        response = adapter.getBeaconAlleleResponse(request);
        assertThat(response.getDatasetAlleleResponses()).hasSize(1);
        assertThat(response.getError()).isNotNull();
        assertThat(response.getError().getErrorCode()).isEqualTo(404);
        assertThat(response.getExists()).isNull();
        assertThat(response.getDatasetAlleleResponses().stream().allMatch(x-> x.getError() != null)).isTrue();

    }

    @Test
    public void testQuery() throws BeaconException {
        BeaconAdapter adapter = new VcfBeaconAdapter();
        adapter.initAdapter(adapterConfig);

        Beacon beacon = adapter.getBeacon();

        for (BeaconAlleleRequest request : beacon.getSampleAlleleRequests()) {
            BeaconAlleleResponse response = adapter.getBeaconAlleleResponse(request);
            assertThat(response.getError()).isNull();
            assertThat(response.getExists()).isTrue();

            BeaconAlleleResponse response2 = adapter.getBeaconAlleleResponse(request.getReferenceName(), request.getStart(), request.getReferenceBases(), request
                    .getAlternateBases(), request.getAssemblyId(), request.getDatasetIds(), request.getIncludeDatasetResponses());

            assertThat(response2.getError()).isNull();
            assertThat(response2.getExists()).isTrue();

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
        assertThat(response.getExists()).isTrue();
        assertThat(response.getDatasetAlleleResponses().stream().filter(x -> x.getExists()).count()).isEqualTo(1);
        assertThat(response.getDatasetAlleleResponses().stream().filter(x -> !x.getExists()).count()).isEqualTo(1);
        assertThat(response.getDatasetAlleleResponses().stream().allMatch(x -> x.getError() == null)).isTrue();

    }

    private static AdapterConfig createConfig() {
        ClassLoader cl = VcfBeaconAdapterTest.class.getClassLoader();
        try {
            String testGtVcf = cl.getResource(GT_FILE).toURI().getPath();
            String testNoGtVcf = cl.getResource(NO_GT_FILE).toURI().getPath();
            String beaconJson = cl.getResource(BEACON_FILE).toURI().getPath();
            List<ConfigValue> values = new ArrayList<>();


            values.add(new ConfigValue("filenames", String.format("%s,%s", testGtVcf, testNoGtVcf)));
            values.add(new ConfigValue("beaconJsonFile", beaconJson));

            return new AdapterConfig("vcf_test_beacon", AdapterConfig.class.getCanonicalName(), values);
        } catch (URISyntaxException e) {
            throw new NullPointerException(e.getMessage());
        }

    }

    private static AdapterConfig createConfig(String... filenames) {
        ClassLoader cl = VcfBeaconAdapterTest.class.getClassLoader();
        try {
            String beaconJson = cl.getResource(BEACON_FILE).toURI().getPath();
            List<ConfigValue> values = new ArrayList<>();

            String files = StringUtils.join(filenames, ",");


            values.add(new ConfigValue("filenames", String.format(files)));
            values.add(new ConfigValue("beaconJsonFile", beaconJson));

            return new AdapterConfig("vcf_test_beacon", AdapterConfig.class.getCanonicalName(), values);
        } catch (URISyntaxException e) {
            throw new NullPointerException(e.getMessage());
        }

    }



}