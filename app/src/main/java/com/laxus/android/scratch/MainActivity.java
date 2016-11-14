package com.laxus.android.scratch;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.laxus.android.scratchlayout.ScratchLayout;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;

public class MainActivity extends AppCompatActivity implements ScratchLayout.OnRevealListener, View.OnClickListener {

    private ScratchLayout mSlPokemonImage;
    private ScratchLayout mSlPokemonName;
    private ScratchLayout mSlLuckyNumber;
    private ImageView mPokemonImage;
    private TextView mPokemonName;
    private TextView mLuckyNumber;
    private EditText mLuckNumberEdit;

    private TextView mType;
    private TextView mAb1;
    private TextView mAb2;
    private TextView mHeight;
    private TextView mWeight;

    private int mLucky = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        randomPokemon();
    }

    private void bindViews() {
        mSlPokemonImage = (ScratchLayout) findViewById(R.id.sl_pokemon_image);
        mSlPokemonName = (ScratchLayout) findViewById(R.id.sl_pokemon_name);
        mSlLuckyNumber = (ScratchLayout) findViewById(R.id.sl_lucky_number);
        mPokemonImage = (ImageView) findViewById(R.id.pokemon_image);
        mPokemonName = (TextView) findViewById(R.id.pokemon_name);
        mLuckNumberEdit = (EditText) findViewById(R.id.lucky_number_edit);
        mLuckyNumber = (TextView) findViewById(R.id.lucky_number);

        mType = (TextView) findViewById(R.id.type);
        mAb1 = (TextView) findViewById(R.id.ab1);
        mAb2 = (TextView) findViewById(R.id.ab2);
        mHeight = (TextView) findViewById(R.id.height);
        mWeight = (TextView) findViewById(R.id.weight);

        mSlPokemonImage.addOnRevealListener(this);
        mSlLuckyNumber.addOnRevealListener(this);
        mSlPokemonName.setScratchEnable(false);

        ScratchLayout reset = (ScratchLayout) findViewById(R.id.sl_reset);
        reset.setMask(new TextMaskDrawable("Pokemon Master", (int) (getResources().getDisplayMetrics().density * 26)));

        findViewById(R.id.reset).setOnClickListener(this);

    }

    private void randomPokemon() {
        try {
            String[] pokemon = getAssets().list("pokemon");
            if (pokemon.length > 0) {
                SecureRandom random = new SecureRandom();
                inflatePokemon(mPokemonImage, mPokemonName, pokemon[random.nextInt(pokemon.length)]);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void inflatePokemon(ImageView image, TextView text, String fileName) {
        InputStream is = null;
        try {
            is = getAssets().open("pokemon/" + fileName);
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            if (bitmap != null) {
                image.setImageBitmap(bitmap);
            }
            text.setText(fileName.substring(0, fileName.length() - 4));
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onRevealed(ScratchLayout scratch) {
        switch (scratch.getId()) {
            case R.id.sl_pokemon_image:
                mSlPokemonName.reveal();
                mType.setText("Pokemon");
                mAb1.setText("eat");
                mAb2.setText("sleep");
                mWeight.setText("growing");
                mHeight.setText("growing");
                break;
            case R.id.sl_lucky_number:
                if (mLucky != -1 && mLucky == Integer.valueOf(mLuckyNumber.getText().toString())) {
                    Toast.makeText(this, "Pokemon Captured", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "...", Toast.LENGTH_SHORT).show();
                }
                break;
            default:
                break;
        }
    }

    private void resetPokemonSl() {
        mSlPokemonImage.reset();
        mSlPokemonName.reset();
        mSlLuckyNumber.reset();
    }

    private void resetInfo() {
        String info = "???";
        mType.setText(info);
        mAb1.setText(info);
        mAb2.setText(info);
        mWeight.setText(info);
        mHeight.setText(info);
    }

    private void setLuckNumber() {
        SecureRandom random = new SecureRandom();
        int num = random.nextInt(100);
        mLuckyNumber.setText("" + num);
    }

    @Override
    public void onClick(View view) {
        String num = mLuckNumberEdit.getText().toString();
        if (num.length() == 0) {
            Toast.makeText(this, "try lucky number", Toast.LENGTH_SHORT).show();
        } else {
            mLucky = Integer.valueOf(num);
            resetPokemonSl();
            resetInfo();
            randomPokemon();
            setLuckNumber();
        }
    }
}
