import cv2
import time
from ultralytics import YOLO

# Load YOLO model
model = YOLO("yolo11n.pt")

# Open webcam
cap = cv2.VideoCapture(0)

if not cap.isOpened():
    print("ERROR: Could not open webcam.")
    raise SystemExit

print("================================")
print("YOLO PERFORMANCE TEST")
print("================================")
print("Press Q to stop the test.")
print()

frame_count = 0
total_time = 0

while True:
    ret, frame = cap.read()

    if not ret:
        print("ERROR: Could not read webcam frame.")
        break

    # Start timer
    start_time = time.perf_counter()

    # Run YOLO
    results = model(frame, verbose=False)

    # Stop timer
    end_time = time.perf_counter()

    processing_time = end_time - start_time

    total_time += processing_time
    frame_count += 1

    # Calculate FPS
    fps = 1 / processing_time if processing_time > 0 else 0

    # Draw detections
    annotated_frame = results[0].plot()

    # Display performance information
    cv2.putText(
        annotated_frame,
        f"FPS: {fps:.2f}",
        (20, 40),
        cv2.FONT_HERSHEY_SIMPLEX,
        1,
        (0, 255, 0),
        2
    )

    cv2.putText(
        annotated_frame,
        f"Processing: {processing_time * 1000:.1f} ms",
        (20, 80),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.7,
        (0, 255, 0),
        2
    )

    cv2.imshow(
        "Smart Glasses - Performance Test",
        annotated_frame
    )

    # Press Q to stop
    if cv2.waitKey(1) & 0xFF == ord("q"):
        break

cap.release()
cv2.destroyAllWindows()

# Calculate average performance
if frame_count > 0:
    average_time = total_time / frame_count
    average_fps = 1 / average_time

    print("================================")
    print("PERFORMANCE RESULTS")
    print("================================")
    print(f"Frames tested     : {frame_count}")
    print(f"Average time/frame: {average_time * 1000:.2f} ms")
    print(f"Average FPS       : {average_fps:.2f}")
    print("================================")
else:
    print("No frames were processed.")