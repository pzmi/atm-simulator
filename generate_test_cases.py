import random
import copy
import json
import math
import requests

atm = {"name": 123, "location": [50.0622357, 19.9359087]}

def generate_atms(n):
    generated = [copy.deepcopy(atm) for x in range(n)]
    used = set()

    for x in generated:
        some_random = rand()
        while some_random in used:
            some_random = rand()

        used.add(some_random)
        x["name"] = some_random

    return generated


def rand():
    return int(math.floor(random.uniform(0, 99999999)))


def atms_json(n):
    return json.dumps(generate_atms(n))

def replace_atms_in(file_name, n):
    with open(file_name, "r") as rf:
        config = json.load(rf)
        config["atms"] = generate_atms(n)

    with open(file_name, "w") as wf:
        json.dump(config, wf)


def start():
    r = requests.get("http://localhost:8080/config/default", headers={"Accept": "application/json"})
    b = requests.post("http://localhost:8080/simulation/simulation", headers={"content-type": "application/json"},
                      data=r.content)
    print(b.status_code)
    print(b.json())


