import tensorflow as tf
import keras


def export_saved_model(path):
    converter = tf.lite.TFLiteConverter.from_saved_model(path)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()

    with open('model.tflite', 'wb') as file:
        file.write(tflite_model)

    check_tflite()


def export_h5_model(path):
    model = keras.models.load_model(path)
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()

    with open('model.tflite', 'wb') as file:
        file.write(tflite_model)


def export_converted_model():
    model = tf.saved_model.load("mobilenetssd_coco/saved_model")

    output_tensors = {
        "num_detections": tf.TensorSpec(shape=[1], dtype=tf.float32),
        "detection_boxes": tf.TensorSpec(shape=[1, 100, 4], dtype=tf.float32),
        "detection_classes": tf.TensorSpec(shape=[1, 100], dtype=tf.float32),
        "raw_detection_scores": tf.TensorSpec(shape=[1, 1917, 91], dtype=tf.float32),
        "detection_multiclass_scores": tf.TensorSpec(shape=[1, 100, 91], dtype=tf.float32),
        "raw_detection_boxes": tf.TensorSpec(shape=[1, 1917, 4], dtype=tf.float32),
        "detection_scores": tf.TensorSpec(shape=[1, 100], dtype=tf.float32),
        "detection_anchor_indices": tf.TensorSpec(shape=[1, 100], dtype=tf.float32)
    }

    @tf.function(input_signature=[tf.TensorSpec(shape=[1, 320, 320, 3], dtype=tf.uint8)])
    def model_function(input_tensor):
        # Run the model to get the outputs
        outputs = model(input_tensor)

        # Edit the outputs using a map,
        new_outputs = {}
        for key, output in outputs.items():
            new_outputs[key] = tf.reshape(output, output_tensors[key].shape)

        return new_outputs

    tf.saved_model.save(model, "modified_saved_model", signatures={"serving_default": model_function})

    export_saved_model("modified_saved_model")
    check_tflite()


def check_tflite():
    interpreter = tf.lite.Interpreter(model_path='model.tflite')

    interpreter.allocate_tensors()

    input_details = interpreter.get_input_details()
    for detail in input_details:
        print(detail['name'], detail['shape'])

    output_details = interpreter.get_output_details()
    for detail in output_details:
        print(detail['name'], detail['shape'])


export_saved_model("mobilenetssd_coco/saved_model")
# export_h5_model("keras_yolov8s_coco")
#export_converted_model()
