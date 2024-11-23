package com.Yproject.dailyw.ui.home;

import android.content.Context;
import android.content.SharedPreferences;

import com.Yproject.dailyw.util.weightStructure;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class homeRepository {
    private SharedPreferences sharedPreferences;
    private Gson gson;

    public homeRepository(Context context) {
        sharedPreferences = context.getSharedPreferences("WeightData", Context.MODE_PRIVATE);
        gson = new Gson();
    }

    public List<weightStructure> getWeights(String month) {
        String json = sharedPreferences.getString(month, "[]");
        Type type = new TypeToken<List<weightStructure>>(){}.getType();
        List<weightStructure> weights = gson.fromJson(json, type);

        weights.sort(Comparator.comparing(weightStructure::getDate));

        return weights;
    }
}
