@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import groovy.transform.*
import com.ibm.dbb.*
import com.ibm.dbb.build.*
import com.ibm.jzos.ZFile

@Field BuildProperties props = BuildProperties.getInstance()

@Field def testUtils = loadScript(new File("../utils/testUtilities.groovy"))

println "\n** Executing test script fullBuild_debug.groovy"

// Get the DBB_HOME location
def dbbHome = EnvVars.getHome()
if (props.verbose) println "** DBB_HOME = ${dbbHome}"

// Create full build command
def fullBuildCommand = [] 
fullBuildCommand << "${dbbHome}/bin/groovyz"
fullBuildCommand << "${props.zAppBuildDir}/build.groovy"
fullBuildCommand << "--workspace ${props.workspace}"
fullBuildCommand << "--application ${props.app}"
fullBuildCommand << (props.outDir ? "--outDir ${props.outDir}" : "--outDir ${props.zAppBuildDir}/out")
fullBuildCommand << "--hlq ${props.hlq}"
fullBuildCommand << "--logEncoding UTF-8"
fullBuildCommand << "--url ${props.url}"
fullBuildCommand << "--id ${props.id}"
fullBuildCommand << (props.pw ? "--pw ${props.pw}" : "--pwFile ${props.pwFile}")
fullBuildCommand << "--verbose"
fullBuildCommand << (props.propFiles ? "--propFiles ${props.propFiles}" : "")
fullBuildCommand << "--fullBuild"
fullBuildCommand << "--debug"

// Run full build 
println "** Executing ${fullBuildCommand.join(" ")}"
def process = ['bash', '-c', fullBuildCommand.join(" ")].execute()
def outputStream = new StringBuffer();
process.waitForProcessOutput(outputStream, System.err)

//Validate build results
println "** Validating full build results"
def expectedFilesBuiltList = props.fullBuild_debug_expectedFilesBuilt.split(',')

@Field def assertionList = []
PropertyMappings expectedBuildOutputsMapping = new PropertyMappings('fullBuild_debug_expectedBuildOutputs')

try {
	// Validate clean build
	assert outputStream.contains("Build State : CLEAN") : "*! FULL BUILD WITH DEBUG FAILED\nOUTPUT STREAM:\n$outputStream\n"

	// Validate expected number of files built
	def numFullFiles = expectedFilesBuiltList.size()
	assert outputStream.contains("Total files processed : ${numFullFiles}") : "*! TOTAL FILES PROCESSED ARE NOT EQUAL TO ${numFullFiles}\nOUTPUT STREAM:\n$outputStream\n"

	// Validate expected built files in output stream
	assert expectedFilesBuiltList.count{ i-> outputStream.contains(i) } == expectedFilesBuiltList.size() : "*! FILES PROCESSED IN THE FULL BUILD DOES NOT CONTAIN THE LIST OF FILES PASSED ${expectedFilesBuiltList}\nOUTPUT STREAM:\n$outputStream\n"
	
	// Validate the expected outputs
	def buildReportFile = testUtils.getBuildReportFromStream(outputStream)
	if (buildReportFile) {
		def buildReport = testUtils.parseBuildReport(buildReportFile)
		if (buildReport) {
			expectedFilesBuiltList.each{ expectedFile ->
				def expectedOutputs = expectedBuildOutputsMapping.getValue(expectedFile)
				expectedOutputs.split(",").each { expectedOutput ->
					(member, deployType) = expectedOutput.split(":")
					assert testUtils.buildReportIncludesOutput(buildReport, member, deployType)  : "*! EXPECTED OUTPUT ($member) with deployType ($deployType) not found in buildreport \nOUTPUT STREAM:\n$outputStream\n"
				}
			}
		}
	}
	
	
	println "**"
	println "** FULL BUILD TEST : PASSED **"
	println "**"
}
catch(AssertionError e) {
	def result = e.getMessage()
	assertionList << result;
	props.testsSucceeded = 'false'
}
finally {
	cleanUpDatasets()
	if (assertionList.size()>0) {
		println "\n***"
	println "**START OF FAILED FULL BUILD WITH DEBUG TEST RESULTS**\n"
	println "*FAILED FULL BUILD WITH DEBUG RESULTS*\n" + assertionList
	println "\n**END OF FAILED FULL BUILD WITH DEBUG**"
	println "***"
  }
	
}

// script end

//*************************************************************
// Method Definitions
//*************************************************************

def cleanUpDatasets() {
	def segments = props.fullBuild_debug_datasetsToCleanUp.split(',')
	
	println "Deleting full build PDSEs ${segments}"
	segments.each { segment ->
	    def pds = "'${props.hlq}.${segment}'"
	    if (ZFile.dsExists(pds)) {
	       if (props.verbose) println "** Deleting ${pds}"
	       ZFile.remove("//$pds")
	    }
	}
}
