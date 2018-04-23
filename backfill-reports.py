import requests
import time

filename = "missed_reports_from_end"
missed_reports = open(filename, "r")
count = 0
for line in missed_reports:
    inst, msa, report = line.split(",")
    report = report.rstrip()
    count = count + 1
    #input("<enter> to generate inst {}, msa {}, report {} ({})".format(inst, msa, report, count))
    time.sleep(0.5)
    requests.post("http://localhost:8081/disclosure/{}/{}/{}".format(inst, msa, report))
