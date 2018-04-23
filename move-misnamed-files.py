import requests
import subprocess
import re

def main():
    totalMSAs = len(msaList)
    print("{} msas to check...".format(totalMSAs))

    for i, msa in enumerate(msaList):
        print("****** MSA: {} ({} of {}) ****".format(msa, i, totalMSAs))

        for report in renames:
            fromLocation = s3BaseUrl + msa + "/" + report + ".txt"
            toLocation = s3BaseUrl + msa + "/" + renames[report] + ".txt"
            command = "aws s3 mv {} {}".format(fromLocation, toLocation)
            output = subprocess.getoutput(command)
            print(output)


renames = {
"A41": "4-1",
"A42": "4-2",
"A43": "4-3",
"A44": "4-4",
"A45": "4-5",
"A46": "4-6",
"A47": "4-7",
"A51": "5-1",
"A52": "5-2",
"A53": "5-3",
"A54": "5-4",
"A56": "5-6",
"A57": "5-7",
"A9": "9",
"AI": "i",
}

def cleanOutput(line):
    ln = line.lstrip().strip("/")
    return re.sub(r"^PRE ","",ln)

def lsList(uri):
    output = subprocess.getoutput("aws s3 ls " + uri)
    ls = []
    for line in output.split('\n'):
        cleaned = cleanOutput(line)
        ls.append(cleaned)
    return ls



s3BaseUrl = "s3://cfpb-hmda-public/prod/reports/aggregate/2017/"
msaList = lsList(s3BaseUrl)

main()

