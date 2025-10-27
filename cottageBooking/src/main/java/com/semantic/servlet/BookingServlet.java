package com.semantic.servlet;

import com.semantic.service.SemanticCottageService;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * BookingServlet
 * Receive HTTP request and call SemanticCottageService to return JSON
 */
@WebServlet("/booking")
public class BookingServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");

        String bookerName = request.getParameter("bookerName");
        int numPeople = parseInt(request.getParameter("numPeople"), 1);
        int numBedrooms = parseInt(request.getParameter("numBedrooms"), 1);
        float maxLakeDist = parseFloat(request.getParameter("maxLakeDist"), Float.MAX_VALUE);
        String city = request.getParameter("city");
        float maxCityDist = parseFloat(request.getParameter("maxCityDist"), Float.MAX_VALUE);
        int numDays = parseInt(request.getParameter("numDays"), 1);
        String startDateStr = request.getParameter("startDate");
        int shiftDays = parseInt(request.getParameter("shiftDays"), 0);

        // Initialize semantic layer services
        String ontologyPath = getServletContext().getRealPath("/onto/cottageOntology.owl");
        String dataPath = getServletContext().getRealPath("/onto/cottageData.rdf");
        SemanticCottageService service = new SemanticCottageService(ontologyPath, dataPath);

        JSONArray resultArray = service.findCottages(
                bookerName, numPeople, numBedrooms, maxLakeDist, city,
                maxCityDist, numDays, startDateStr, shiftDays
        );

        JSONObject responseJson = new JSONObject();
        responseJson.put("bookings", resultArray);

        try (PrintWriter out = response.getWriter()) {
            out.print(responseJson.toString(2));
        }
    }

    private static int parseInt(String v, int def) {
        try { return Integer.parseInt(v); } catch (Exception e) { return def; }
    }
    private static float parseFloat(String v, float def) {
        try { return Float.parseFloat(v); } catch (Exception e) { return def; }
    }
}
