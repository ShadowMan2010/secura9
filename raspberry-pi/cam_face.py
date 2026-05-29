import cv2
import face_recognition

# Start webcam
video_capture = cv2.VideoCapture(0)

while True:
    ret, frame = video_capture.read()

    # Convert to RGB (important)
    rgb_frame = frame[:, :, ::-1]

    # Detect faces
    face_locations = face_recognition.face_locations(rgb_frame)

    # Draw boxes
    for (top, right, bottom, left) in face_locations:
        cv2.rectangle(frame, (left, top), (right, bottom), (0, 255, 0), 2)

    # Show video
    cv2.imshow("CyberCam", frame)

    # Press Q to exit
    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

video_capture.release()
cv2.destroyAllWindows()
