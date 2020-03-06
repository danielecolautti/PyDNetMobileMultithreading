package client.java;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;

import androidx.appcompat.app.AppCompatActivity;

public class FirstActivity extends AppCompatActivity {

    private Button button;
    private EditText ip,port;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first);

        ip = (EditText) findViewById(R.id.editTextIp);
        port = (EditText) findViewById(R.id.editTextPort);
        button = (Button) findViewById(R.id.buttonStart);

        button.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View arg0) {
                Intent changeActivity = new Intent(FirstActivity.this,MainActivity.class);
                changeActivity.putExtra("IP", ip.getText().toString());
                changeActivity.putExtra("PORT", port.getText().toString());
                startActivity(changeActivity);
            }
        });
    }

}
