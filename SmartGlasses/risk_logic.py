def calculate_risk(distance, moving_toward=False):
    """
    Calculate obstacle risk based on distance
    and whether the object is moving toward the user.

    Prototype thresholds only.
    """

    if distance < 1.5:
        risk = "DANGER"

    elif distance <= 3.0:
        risk = "WARNING"

    else:
        risk = "SAFE"

    # Increase risk if an object is approaching
    if moving_toward and risk == "SAFE":
        risk = "WARNING"

    return risk


print("================================")
print("OBSTACLE RISK LOGIC TEST")
print("================================")

# Test 1
distance = 4.0
moving_toward = False

risk = calculate_risk(distance, moving_toward)

print("Distance:", distance, "meters")
print("Moving toward user:", moving_toward)
print("Risk:", risk)

print("--------------------------------")

# Test 2
distance = 2.0
moving_toward = False

risk = calculate_risk(distance, moving_toward)

print("Distance:", distance, "meters")
print("Moving toward user:", moving_toward)
print("Risk:", risk)

print("--------------------------------")

# Test 3
distance = 1.0
moving_toward = False

risk = calculate_risk(distance, moving_toward)

print("Distance:", distance, "meters")
print("Moving toward user:", moving_toward)
print("Risk:", risk)

print("--------------------------------")

# Test 4
distance = 4.0
moving_toward = True

risk = calculate_risk(distance, moving_toward)

print("Distance:", distance, "meters")
print("Moving toward user:", moving_toward)
print("Risk:", risk)

print("================================")
print("RISK LOGIC TEST COMPLETED")
print("================================")