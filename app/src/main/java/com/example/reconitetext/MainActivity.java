package com.example.reconitetext;

import androidx.annotation.NonNull;
import android.annotation.SuppressLint;
import androidx.appcompat.app.AppCompatActivity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;


import com.google.android.gms.tasks.Task;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetector;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetectorOptions;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;

import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity{

    private Button btnCapture, btnDetectedMRZ, DetectedQR, btnChoosefile;
    private ImageView imgView;
    private TextView txtView;
    private Bitmap imageBitmap = null;
    private InputImage image = null;
    private FirebaseVisionImage imageVision = null;
    static final int REQUEST_IMAGE_CAPTURE = 1, FILE_SELECT_CODE = 2;

    @SuppressLint("ResourceType")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnCapture = findViewById(R.id.btn_capture);
        btnDetectedMRZ = findViewById(R.id.btn_detected_mrz);
        DetectedQR = findViewById(R.id.btn_detected_qr);
        btnChoosefile = findViewById(R.id.btn_choosefile);
        imgView = findViewById(R.id.img_view);
        txtView = findViewById(R.id.txt_view);


        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dispatchTakePictureIntent();
            }
        });
        btnChoosefile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFileChooser();
            }
        });
        DetectedQR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recognizeQR();
            }
        });

        btnDetectedMRZ.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recognizeText();
            }
        });
    }

    // recognize QR code
    private void recognizeQR() {
        if(imageVision == null){
            return;
        }
        // set_detector_options
        FirebaseVisionBarcodeDetectorOptions options =
                new FirebaseVisionBarcodeDetectorOptions.Builder()
                        .setBarcodeFormats(
                                FirebaseVisionBarcode.FORMAT_QR_CODE,
                                FirebaseVisionBarcode.FORMAT_AZTEC)
                        .build();

        FirebaseVisionBarcodeDetector detector = FirebaseVision.getInstance()
                .getVisionBarcodeDetector(options);
        Task<List<FirebaseVisionBarcode>> result = detector.detectInImage(imageVision)
            .addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionBarcode>>() {
                @Override
                public void onSuccess(List<FirebaseVisionBarcode> barcodes) {
                    String valueQR = "";
                    for (FirebaseVisionBarcode barcode: barcodes) {
                        Rect bounds = barcode.getBoundingBox(); // do some thing if you want
                        Point[] corners = barcode.getCornerPoints(); // do some thing if you want
                        String rawValue = barcode.getRawValue(); // do some thing if you want
                        int valueType = barcode.getValueType(); // do some thing if you want
                        // See API reference for complete list of supported types
                        valueQR = barcode.getRawValue();
                        switch (valueType) {
                            case FirebaseVisionBarcode.FORMAT_ALL_FORMATS:
                                valueQR = barcode.getDisplayValue();
                                break;
                        }
                    }
                    if(barcodes.size() == 0){
                        onOpenDialog("No QR code");
                    }else{
                        onOpenDialog("valueQR: " + valueQR);
                    }
                }
            })
            .addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Toast.makeText(MainActivity.this, "Error " + e.getMessage(),  Toast.LENGTH_SHORT).show();
                    Log.d("Error ", e.getMessage());
                }
            });
    }


    // recognize text
    private void recognizeText() {
        if(image == null){
            return;
        }
        TextRecognizer recognizer = TextRecognition.getClient();
        // process image
        Task<Text> result = recognizer.process(image)
            .addOnSuccessListener(new OnSuccessListener<Text>() {
                @Override
                public void onSuccess(Text visionText) {
                    // Task completed successfully
                    String txtRes = "";
                    String mrz = "";
                    int length_mrz = 0;
                    for (Text.TextBlock block : visionText.getTextBlocks()) {
                        Rect boundingBox = block.getBoundingBox();
                        Point[] cornerPoints = block.getCornerPoints();
                        String text = block.getText();
                        txtRes = txtRes + ", \n" + text;
                        if(isMRZCode(block.getText())){
                            length_mrz++;
                            mrz = block.getText(); // last txt is mrz txt
                        }
                    }
                    onOpenDialog("Full data:" + txtRes);
                    if(length_mrz == 0){
                        onOpenDialog("no mrz code");
                    }
                    else if(length_mrz == 1){
                        onOpenDialog("mrz code: "+ mrz);
                    }
                    else{
                        onOpenDialog("Some thing wrong or mrz code has so many line!");
                    }
                }
            })
            .addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Toast.makeText(MainActivity.this, "Error " + e.getMessage(),  Toast.LENGTH_SHORT).show();
                    Log.d("Error ", e.getMessage());
                }
            });
    }

    // not use
    private void processTextBlock(Text result) {
        // [START mlkit_process_text_block]
        String resultText = result.getText();
        txtView.setText(resultText);
        for (Text.TextBlock block : result.getTextBlocks()) {
            String blockText = block.getText();
            Point[] blockCornerPoints = block.getCornerPoints();
            Rect blockFrame = block.getBoundingBox();
            for (Text.Line line : block.getLines()) {
                String lineText = line.getText();
                Point[] lineCornerPoints = line.getCornerPoints();
                Rect lineFrame = line.getBoundingBox();
                for (Text.Element element : line.getElements()) {
                    String elementText = element.getText();
                    Point[] elementCornerPoints = element.getCornerPoints();
                    Rect elementFrame = element.getBoundingBox();
                }
            }
        }
        // [END mlkit_process_text_block]
    }

    // not use
    private TextRecognizer getTextRecognizer() {
        // [START mlkit_local_doc_recognizer]
        TextRecognizer detector = TextRecognition.getClient();
        // [END mlkit_local_doc_recognizer]

        return detector;
    }


    // open camera
    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if(takePictureIntent.resolveActivity(getPackageManager()) != null){
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    // choose file from yourphone
    private void showFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(
                    Intent.createChooser(intent, "Select a File to Upload"),
                    FILE_SELECT_CODE);
        } catch (android.content.ActivityNotFoundException ex) {
            // Potentially direct the user to the Market with a Dialog
            Toast.makeText(this, "Please install a File Manager.",
                    Toast.LENGTH_SHORT).show();
        }
    }


    // run after take a photo or choose some file
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) { // take a photo
            Bundle extras = data.getExtras();
            imageBitmap = (Bitmap) extras.get("data");
            imgView.setImageBitmap(imageBitmap);
            // set input image
            image = InputImage.fromBitmap(imageBitmap, 90); // vertical rotate default (rotationDegree)
            // set firebase image vision
            imageVision = FirebaseVisionImage.fromBitmap(imageBitmap);
        }
        else if(requestCode == FILE_SELECT_CODE && resultCode == RESULT_OK){ // choose a file
            Uri uri = data.getData();
            try {
                imageBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
                imgView.setImageBitmap(imageBitmap);
                // set input image
                image = InputImage.fromBitmap(imageBitmap, 90); // vertical rotate default (rotationDegree)
                // set firebase image vision
                imageVision = FirebaseVisionImage.fromBitmap(imageBitmap);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // open dialog alert a txt
    private void onOpenDialog(String txt){
        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setTitle("Result");
        alertDialog.setMessage(txt);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        alertDialog.show();
    }
    private boolean isMRZCode(String txt){
        if(txt.contains("<")){
            return true;
        }
        return false;
    }
}