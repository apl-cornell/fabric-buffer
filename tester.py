import csv
import json
import itertools
import subprocess
from os.path import devnull
from pprint import pprint

WORKER_OUT_FILE = "workers.csv"
STORE_OUT_FILE = "stores.csv"


def scalar_to_iterable(s):
    return s if hasattr(s, "__iter__") else [s]


def cli_param_string(ps, vals):
    arguments = []
    for parameter, value in zip(ps, vals):
        arguments.append("-%s=%s" % (parameter, value))
    return " ".join(arguments)


def csv_to_dicts(file_name, **kwargs):
    with open(file_name, mode='r') as f:
        reader = csv.DictReader(f, **kwargs)
        return list(reader)


with open('config.json') as config:
    data = json.loads(config.read())


data = {
    key: scalar_to_iterable(value) for key, value in data.items()
}

parameters, values = zip(*data.items())

# counter for testing, used to limit the number of runs
i = 0
for value_combination in itertools.product(*values):
    if i == 3:
        break
    param_string = cli_param_string(parameters, value_combination)
    cmd = './fbuffer %s' % param_string

    # this is a blocking call, since we need to wait for the process to finish
    print('Running test: %s' % cmd)
    subprocess.call(cmd, shell=True)
    print('Test finished, collecting results...')

    workers = csv_to_dicts(WORKER_OUT_FILE, delimiter=',')
    stores = csv_to_dicts(STORE_OUT_FILE, delimiter=',')

    # TODO: do something with the data
    pprint(workers)
    pprint(stores)
    i += 1
