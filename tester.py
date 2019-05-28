import csv
import errno
import functools
import itertools
import json
import operator
import os
import subprocess
import sys

import pandas as pd

WORKER_OUT_FILE = "workers.csv"
STORE_OUT_FILE = "stores.csv"
try:
    OUT_FILE = sys.argv[1]
    SUMMARY_OUT_FILE = sys.argv[2]
except IndexError:
    print("Error: output file(s) not provided")
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


def ensure_path_exists(path):
    dirname = os.path.dirname(path)
    if dirname != '' and not os.path.exists(dirname):
        try:
            os.makedirs(os.path.dirname(path))
        except OSError as e:
            if e.errno != errno.EEXIST:
                raise


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

summary_rows = []

for value_combination in itertools.product(*values):
    param_string = cli_param_string(parameters, value_combination)
    cmd = './fbuffer %s %s' % (param_string, extra_params)

    # this is a blocking call, since we need to wait for the process to finish
    print('Running test %d of %d: %s' % (i + 1, total_combinations, cmd))
    subprocess.call(cmd, shell=True)
    print('Test finished, collecting results...')

    workers = csv_to_dicts(WORKER_OUT_FILE, delimiter=',')
    stores = csv_to_dicts(STORE_OUT_FILE, delimiter=',')

    worker_df = pd.DataFrame(workers)
    store_df = pd.DataFrame(stores)

    worker_completed = worker_df['Completed'].apply(int).values.sum()
    store_aborted = \
        store_df[['AbortedLock', 'AbortedVC', 'BufferAbortedLock', 'BufferAbortedVC']].applymap(int).values.sum()

    row = dict(zip(parameters, list(value_combination)))
    row['WorkerCompleted'] = worker_completed
    row['StoreAborted'] = store_aborted
    summary_rows.append(row)  # TODO: change this to print row-by-row

    output_data.append({
        "args": {
            param: arg for param, arg in zip(parameters, value_combination)
        },
        "workers": workers,
        "stores": stores
    })
    i += 1

ensure_path_exists(OUT_FILE)
ensure_path_exists(SUMMARY_OUT_FILE)

with open(OUT_FILE, 'w+') as f:
    json.dump(output_data, f, indent=4)
pd.DataFrame(summary_rows).to_csv(SUMMARY_OUT_FILE)

# rows = []
# with open('control.json') as f:
#     data = json.load(f)
#     for d in data:
#         row = d['args'].copy()
#         row['WorkerCompleted'] = sum(
#             [int(worker['Completed']) for worker in d['workers']]
#         )
#         row['AbortedLock'] = sum(
#             [int(store['AbortedLock']) for store in d['stores']]
#         )
#         row['AbortedVC'] = sum(
#             [int(store['AbortedVC']) for store in d['stores']]
#         )
#         row['BufferAbortedLock'] = sum(
#             [int(store['BufferAbortedLock']) for store in d['stores']]
#         )
#         row['BufferAbortedVC'] = sum(
#             [int(store['BufferAbortedVC']) for store in d['stores']]
#         )
#         # row['StoreAborted'] = sum(
#         #     [int(store['AbortedLock']) + int(store['AbortedVC']) + int(store['BufferAbortedLock'])
#         #      + int(store['BufferAbortedLock'])
#         #      for store in d['stores']]
#         # )
#         rows.append(row)

