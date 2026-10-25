from ultralytics import YOLO

# Load the pretrained YOLO11 Nano model
model = YOLO("yolo11n.pt")

print("================================")
print("YOLO MODEL TEST")
print("================================")
print("Model loaded successfully!")
print("Model: YOLO11n")
print("================================")