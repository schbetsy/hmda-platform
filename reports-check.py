import requests
import subprocess
import re

def main():
    missFile = open("missed_reports", "a")
    totalInstitutions = len(instList)
    print("{} institutions to check...".format(totalInstitutions))

    for i, inst in enumerate(instList):
        if (i % 25 == 0):
            print("checking {} ({} of {})".format(inst, i, totalInstitutions))

        msaList = lsList(s3BaseUrl + str(inst) + "/")

        for msa in msaList:
            reportList = fileList(inst, msa)
            if msa == "nationwide":
                missed = checkNationwide(reportList)
            else:
                missed = checkMSAReports(reportList)

            for report in missed:
                missFile.write("{},{},{}\n".format(inst, msa, report))

    missFile.close()


def cleanOutput(line):
    ln = line.lstrip().strip("/")
    return re.sub(r"^PRE ","",ln)

def lsList(uri):
    output = subprocess.getoutput(lsCommand + uri)
    ls = []
    for line in output.split('\n'):
        cleaned = cleanOutput(line)
        ls.append(cleaned)
    return ls

def fileList(inst, msa):
    output = subprocess.getoutput(lsCommand + s3BaseUrl + str(inst) + "/" + msa + "/")
    files = []
    for line in output.split('\n'):
        fileName = re.findall(r"\S+txt",line)[0].split(".")[0]
        files.append(fileName)
    return files

def checkNationwide(list):
    expected = ['A1W', 'A2W', 'A3W', 'BW', 'IRS']
    if len(list) < len(expected):
        missing = expected
        for report in list:
            missing.remove(report)
    else:
        missing = []
    return missing

def checkMSAReports(list):
    expected = ['1', '11-1', '11-10', '11-2', '11-3', '11-4', '11-5', '11-6', '11-7', '11-8', '11-9', '12-1', '12-2', '2', '3-1', '3-2', '4-1', '4-2', '4-3', '4-4', '4-5', '4-6', '4-7', '5-1', '5-2', '5-3', '5-4', '5-6', '5-7', '7-1', '7-2', '7-3', '7-4', '7-5', '7-6', '7-7', '8-1', '8-2', '8-3', '8-4', '8-5', '8-6', '8-7', 'A1', 'A2', 'A3', 'A4W', 'B']
    if len(list) < len(expected):
        missing = expected
        for report in list:
            missing.remove(report)
    else:
        missing = []
    return missing


s3BaseUrl = "s3://cfpb-hmda-public/prod/reports/disclosure/2017/"
lsCommand = "aws s3 ls "
#instList = lsList(s3BaseUrl)
instList = [
"754068",
"165589",
"312356",
"330873",
"2989006",
"198073",
"3610567",
"4438478",
"786274",
"352772",
"3874510",
"3187630",
"3228001",
"14-1841762",
"4320694",
"4438423",
"2213046",
"636771",
"412751",
"4438441",
"3955419",
"3871359",
"3877968",
"541307",
"3915431",
"3868694",
"3873447",
"4184083",
"3885806",
"761806",
"4877639",
"4321963",
"4346818",
"3949203",
"26-1193089",
"1002878",
"4183479",
]

main()

            #print(reportList)
        #print(msaList)
        #print("{} MSAs for {}".format(len(msaList) - 1, inst))
        #print(".")
                #if len(missed) > 0:
                    #print("{}: nationwide reports missing: {}".format(inst, missed))
                #if len(missed) > 0:
                    #print("{}: reports for MSA {} missing: {}".format(inst, msa, missed))

