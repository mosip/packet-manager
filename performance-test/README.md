This folder contains performance test scripts and test data of Packet manager module.

### Environment Required:-
***Below modules should be running in kubernetes setup***

* Kernel audit service
* Kernel authmanager service
* Packetmanager service

### Data pre-requisite:-
* Sample Reg ID's values are stored and can be updated based on the environment in the file ridPacketManager.txt.
* Sample document names are stored and can be updated based on the requirement in the file documentNames.txt.
* Sample field names are stored and can be updated based on the requirement in the file fieldNames.txt.
* Sample modality values are stored and can be updated based on the environment in the file modalityValues.txt.
* Sample field values are stored and can be updated based on the environment in the file searchFieldsRequestBody.csv.
* Sample tag values are stored and can be updated based on the environment in the file tags.txt.

** All the above mentioned data pre-requisites files are present in the support files folder.
** According to the execution test data will be required and need to have Reg ID's values in ridPacketManager.txt.
### How to run JMeter scripts:-

* For the test execution part we have a Packet Manager Test Script which will do all the execution tasks.
* We need to take care of the prerequisites first for which we have one thread group Auth Token Generation (Setup) for the creation of authorization token which we will further use in our execution. 
* All the creation tasks which will happen that will automatically save the tokens created to a file in a RunTimeFiles folder which is present in bin folder of JMeter which will be used further by our test script for execution. For the RunTimeFiles folder a variable is defined in the user defined variable section where we can provide the exact path for the mentioned folder.
* In the test script we have 11 execution thread groups for all the scenario's, where the main test execution will take place.
* The Packetmanager module scenario's which we are considering here are - Get Documents, Validate Packet, Search Field, Search Fields, Get Biometrics, Get Audits, Get MetaInfo, Get Tags, Add Tags, Update Tags. Delete Tags and Create Packet.
* All the thread groups will run in a parallel manner & if we don't want to run all of them we can disable the one which we don't want to run.
* Also for viewing the results or output of our test we have added certain listener test elements at the end of our test script which are - View Results Tree, Endpoint Level Report, Scenario Level Report.
* We have a test element named 'User Defined Variables' in Test script where the server IP, server port, protocol, clientId, secretKey, appId, testDuration, rampUp, process, source, refId and runTimeFilePath variables are present and all these are parameterized & can be changed based on our requirements which will further reflect in the entire script.


### Exact steps of execution

	Step 1: Enable only Auth Token Generation (Setup) thread group and toggle/disable the remaining thread groups in the script to create the authorization token value.
	Step 2: Enable the rest of all the scenario based execution thread groups and toggle/disable the first setup thread group.
	Step 5: Make sure test duration and ramp-up is defined in the user defined variable section.
	Step 5: Click on Run/Eexcute the test.
	Step 6: Monitor the metrics during the test run using reports from jmeter, kibana, grafana and Jprofiler.

### Designing the workload model for performance test execution

* Calculation of number of users depending on Transactions per second (TPS) provided by client

* Applying little's law
	* Users = TPS * (SLA of transaction + think time + pacing)
	* TPS --> Transaction per second.

* For the realistic approach we can keep (Think time + Pacing) = 1 second for API testing
	* Calculating number of users for 10 TPS
		* Users= 10 X (SLA of transaction + 1)
		       = 10 X (1 + 1)
			   = 20


			   
### Usage of Constant Throughput timer to control Hits/sec from JMeter

* In order to control hits/ minute in JMeter, it is better to use Timer called Constant Throughput Timer.

* If we are performing load test with 10TPS as hits / sec in one thread group. Then we need to provide value hits / minute as in Constant Throughput Timer
	* Value = 10 X 60
			= 600

* Dropdown option in Constant Throughput Timer
	* Calculate Throughput based on as = All active threads in current thread group
		* If we are performing load test with 10TPS as hits / sec in one thread group. Then we need to provide value hits / minute as in Constant Throughput Timer
	 			Value = 10 X 60
					  = 600
		  
	* Calculate Throughput based on as = this thread
		* If we are performing scalability testing we need to calculate throughput for 10 TPS as 
          Value = (10 * 60 )/(Number of users)
		  
		  
### Description of the scenarios 

* Get Documents (Execution): This scenario is used to get the document information of the packet.

* Validate Packet (Execution): In this scenario packet data validation is done.

* Search Field (Execution): This scenario is used to get specific field from packet using a rid and specific field name.

* Search Fields (Execution): This scenario is used to get list of fields from packet using a rid and specific fields name. 

* Get Biometrics (Execution): This scenario is used to get biometrics of packet. 

* Get Audits (Execution): This scenario is used to get audit data of packet.

* Get MetaInfo (Execution): This scenario is used to get metainfo from packet. 

* Get Tags (Execution): This scenario is used to get information related to tags attached to a packet.

* Add Tags (Execution): This scenario is used to add tags to packet. Tag data can't be repeated for a particular rid or packet.

* Update & Delate Tags (Execution): In this scenario we have 3 endpoints combined in one thread group i.e. add tag, update tag and delete tag. This scenariois used to add or update tags to packet. 

* Create Packet (Execution): This scenario is used to create a reg client packet using a rid.
