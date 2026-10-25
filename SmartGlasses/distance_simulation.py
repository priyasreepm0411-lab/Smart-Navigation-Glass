def calculate_risk(distance, moving_toward=False):

    if distance < 1.5:
        risk = "DANGER"

    elif distance <= 3.0:
        risk = "WARNING"

    else:
        risk = "SAFE"

    # Increase risk if object is moving toward the user
    if moving_toward and risk == "SAFE":
        risk = "WARNING"

    return risk


print("================================")
print("SIMULATED DISTANCE INPUT")
print("================================")

while True:

    user_input = input(
        "Enter obstacle distance in meters "
        "(or type q to quit): "
    )

    # Exit program
    if user_input.lower() == "q":
        print("Simulation stopped.")
        break

    try:
        distance = float(user_input)

        if distance < 0:
            print("Please enter a positive distance.")
            continue

        movement = input(
            "Is the object moving toward the user? "
            "(yes/no): "
        )

        if movement.lower() == "yes":
            moving_toward = True
        else:
            moving_toward = False

        risk = calculate_risk(
            distance,
            moving_toward
        )

        print("--------------------------------")
        print("Distance:", distance, "meters")
        print("Moving toward:", moving_toward)
        print("Risk level:", risk)
        print("--------------------------------")

    except ValueError:
        print("Invalid input. Please enter a number.")

print("================================")
print("DISTANCE SIMULATION COMPLETED")
print("================================")