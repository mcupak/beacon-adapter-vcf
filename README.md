# Vcf Beacon Adapter


The vcf beacon adapter allows you to "beaconize" any number of vcf files under a single beacon. You can then subsequently query the vcf files as you would any normal beacon.
The adapter is meant to be used in conjunction with the Beacon-java rest implementation, or the Beaconizer rest implementation


## Requirements

 - Java 8
 - [Beacon spec v0.3](https://github.com/ga4gh/beacon-team)
 - [Beacon-java](https://github.com/mcupak/beacon-java) / [Beaconizer](https://github.com/mcupak/beaconizer) REST implementation
 - vcf spec 4.0 or later
 - bgzip / tabix

 
## Vcf Files

This adapter uses vcf files to query variant data. The vcf specification can be found [here](https://samtools.github.io/hts-specs/VCFv4.2.pdf). In order to allow for fast, random access of vcf files (which can be gigabytes in size), the input files must be [bgzipped](http://www.htslib.org/doc/tabix.html) and [tab indexed](http://www.htslib.org/doc/tabix.html).
Additionally, the  index file will be expected to have the same name as the vcf file, exception with an additional ".tbi" extension on the end. It is also expected that this file is in the same file folder as the vcf file

## Configuring the Adapter

In order to properly configure the adapter you must call the initAdapter method, supplying it with an AdapterConfig object once a new adapter object has been created.
There are two required parameters for the configuration that must be supplied as ConfigValues to the AdapterConfig object:

#### Required
| Name | Value | example |
|-- | --| -- |
| "filenames" | Comma seperated list of filepaths | "/path/to/vcf/file.vcf.gz,/path/to/another/file.vcf.gz"   |

#### One of the following
| Name | Value | example |
|-- | --| -- |
| "beconJsonFile" | Path to a json file that describes this beaon. The json file is a serialized representation of a beacon and must meet all the requirements of a normal beacon object. | "/path/to/beacon.json" |
| "beaconJson" | Json string that describes this beacon | See below |

Since each vcf file is interpreted as its own dataset, the number of datasets defined in the beaconJson must equal the number of files in the comma seperated list. Each file will be associated to a dataset based on its index in the list. Ie the first dataset will be associated with the first vcf file


```java

//Create the config values
List<ConfigValue> configValues = new ArrayList();

String filenames = "/path/to/your/vcf,/path/to/another/vcf";
String beaconJson = "/path/to/beacon/json/file";

configValues.add(new ConfigValue("filenames",filenames));
configValues.add(new ConfigValue("beaconJson",beaconJson));

AdapterConfig config = new AdapterConfig("vcf_adapter",AdapterConfig.class.getName,configValues);

//Initialize the adapter
BeaconAdapter adapter = new VcfBeaconAdapter();
adapter.initAdapter(config);


```


#### Example beacon.json

```json
{
  "id":"vcf_test_beacon",
  "name":"vcf_test_beacon",
  "apiVersion":"0.3",
  "organization":{
    "id":"vcf_org",
    "name":"Vcf Adapter organization",
    "description":"test organization for the vcf Beacon adapter",
    "address":"99 Lambda Drive, Consumer, Canada",
    "welcomeUrl":"www.welcome.com",
    "contactUrl":"www.contact.com",
    "logoUrl":"www.logo.com"
  },
  "description":"This beacon demonstrates the usage of the VcfBeaconAdapter",
  "version":"1",
  "welcomeUrl":"www.welcome.com",
  "alternativeUrl":"www.alternative.com",
  "createDateTime":"2016/07/23 19:23:11",
  "updateDateTime":"2016/07/23 19:23:11",
  "datasets":[
    {
      "id":"vcf-test-gt",
      "name":"vcf-test-gt",
      "description":"Vcf Adapter test dataset which includes sample / gt info",
      "assemblyId":"grch37",
      "createDateTime":"2016/07/23 19:23:11",
      "updateDateTime":"2016/07/23 19:23:11",
      "version":"1",
      "variantCount":26,
      "sampleCount":1,
      "externalUrl":"www.external.com"
    },
    {
      "id":"vcf-test-no-gt",
      "name":"vcf-test-no-gt",
      "description":"Vcf Adapter test dataset which includes no sample / gt info",
      "assemblyId":"grch37",
      "createDateTime":"2016/07/23 19:23:11",
      "updateDateTime":"2016/07/23 19:23:11",
      "version":"1",
      "variantCount":46,
      "sampleCount":0,
      "externalUrl":"www.external.com"
    }
  ],
  "sampleAlleleRequests":[
    {
      "referenceName":"20",
      "start":76962,
      "referenceBases":"T",
      "alternateBases":"C",
      "assemblyId":"grch37",
      "datasetIds":["vcf-test-gt"],
      "includeDatasetResponses":true

    },
    {
      "referenceName":"1",
      "start":10109,
      "referenceBases":"A",
      "alternateBases":"T",
      "assemblyId":"grch37",
      "datasetIds":["vcf-test-no-gt"],
      "includeDatasetResponses":true
    }
  ]
}
```
