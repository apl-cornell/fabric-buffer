import csv
import errno
import functools
import itertools
import json
import operator
import os
import subprocess
import sys

WORKER_OUT_FILE = "workers.csv"
STORE_OUT_FILE = "stores.csv"
try:
    OUT_FILE = sys.argv[1]
except IndexError:
    print("Error: output file not provided")
    sys.exit(1)


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

output_data = []

# loop counter
i = 0

# total combinations
total_combinations = functools.reduce(operator.mul, map(len, values), 1)
extra_params = "-storefile=%s -workerfile=%s" \
               % (STORE_OUT_FILE, WORKER_OUT_FILE)

times = data['time']
total_time = sum(map(lambda t: t * total_combinations / len(times), times))
hours = (total_time / (1000 * 60 * 60)) % 24
minutes = (total_time / (1000 * 60)) % 60
print('Starting run of %d combinations. Approximate runtime is %d hours and %d minutes.'
      % (total_combinations, hours, minutes))

for value_combination in itertools.product(*values):
    param_string = cli_param_string(parameters, value_combination)
    cmd = './fbuffer %s %s' % (param_string, extra_params)

    # this is a blocking call, since we need to wait for the process to finish
    print('Running test %d of %d: %s' % (i + 1, total_combinations, cmd))
    subprocess.call(cmd, shell=True)
    print('Test finished, collecting results...')

    workers = csv_to_dicts(WORKER_OUT_FILE, delimiter=',')
    stores = csv_to_dicts(STORE_OUT_FILE, delimiter=',')

    output_data.append({
        "args": {
            param: arg for param, arg in zip(parameters, value_combination)
        },
        "workers": workers,
        "stores": stores
    })
    i += 1

dirname = os.path.dirname(OUT_FILE)
if dirname != '' and not os.path.exists(dirname):
    try:
        os.makedirs(os.path.dirname(OUT_FILE))
    except OSError as e:
        if e.errno != errno.EEXIST:
            raise

with open(OUT_FILE, 'w+') as f:
    json.dump(output_data, f, indent=4)
