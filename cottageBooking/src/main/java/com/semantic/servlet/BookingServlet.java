package com.semantic.servlet;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.*;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.sail.memory.MemoryStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Cottage Booking JSON API Servlet
 * Returns JSON-formatted booking suggestions
 */
@WebServlet("/booking")
public class BookingServlet extends HttpServlet {

    private static final String PREFIX = "http://localhost:8080/cottageBooking/onto/cottageOntology.owl#";
    private static final SimpleDateFormat INPUT_FORMAT = new SimpleDateFormat("dd.MM.yyyy");
    private static final SimpleDateFormat XSD_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // ===== 获取输入参数 =====
        String bookerName = request.getParameter("bookerName");
        int numPeople = parseInt(request.getParameter("numPeople"), 1);
        int numBedrooms = parseInt(request.getParameter("numBedrooms"), 1);
        float maxLakeDist = parseFloat(request.getParameter("maxLakeDist"), Float.MAX_VALUE);
        String city = request.getParameter("city");
        float maxCityDist = parseFloat(request.getParameter("maxCityDist"), Float.MAX_VALUE);
        int numDays = parseInt(request.getParameter("numDays"), 1);
        String startDateStr = request.getParameter("startDate");
        int shiftDays = parseInt(request.getParameter("shiftDays"), 0);

        // 计算所有可能日期区间
        List<String[]> possiblePeriods = generatePossiblePeriods(startDateStr, numDays, shiftDays);

        response.setContentType("application/json; charset=UTF-8");
        PrintWriter out = response.getWriter();

        Repository repo = new SailRepository(new MemoryStore());
        repo.init();

        JSONArray resultsArray = new JSONArray();
        int bookingId = 1000;

        try (RepositoryConnection conn = repo.getConnection()) {
            conn.add(new File(getServletContext().getRealPath("/onto/cottageOntology.owl")), "", RDFFormat.TURTLE);
            conn.add(new File(getServletContext().getRealPath("/onto/cottageData.rdf")), "", RDFFormat.TURTLE);

            for (String[] period : possiblePeriods) {
                String startDate = period[0];
                String endDate = period[1];

                StringBuilder queryBuilder = new StringBuilder();
                queryBuilder.append("""
                    PREFIX : <http://localhost:8080/cottageBooking/onto/cottageOntology.owl#>
                    PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                    SELECT ?cottage ?address ?image ?city ?capacity ?bedrooms ?lakeDist ?cityDist ?start ?end
                    WHERE {
                        ?cottage a :Cottage ;
                                 :hasAddress ?address ;
                                 :hasImage ?image ;
                                 :hasNearestCity ?city ;
                                 :hasCapacity ?capacity ;
                                 :hasBedrooms ?bedrooms ;
                                 :hasDistanceToLake ?lakeDist ;
                                 :hasDistanceToCity ?cityDist ;
                                 :hasAvailabilityPeriod ?period .
                        ?period :isFree true ;
                                :hasStartDate ?start ;
                                :hasEndDate ?end .
                """);
                queryBuilder.append("FILTER (?capacity >= " + numPeople + ")\n");
                queryBuilder.append("FILTER (?bedrooms >= " + numBedrooms + ")\n");
                queryBuilder.append("FILTER (?lakeDist <= " + maxLakeDist + ")\n");
                queryBuilder.append("FILTER (?cityDist <= " + maxCityDist + ")\n");

                if (city != null && !city.isEmpty()) {
                    queryBuilder.append("FILTER (lcase(str(?city)) = lcase('" + city + "'))\n");
                }

                queryBuilder.append("""
                    FILTER (xsd:date(?start) <= "%s"^^xsd:date && xsd:date(?end) >= "%s"^^xsd:date)
                    }
                    ORDER BY ?lakeDist
                """.formatted(startDate, endDate));

                TupleQuery query = conn.prepareTupleQuery(QueryLanguage.SPARQL, queryBuilder.toString());
                try (TupleQueryResult result = query.evaluate()) {
                    while (result.hasNext()) {
                        BindingSet row = result.next();
                        JSONObject booking = new JSONObject();

                        booking.put("bookerName", bookerName);
                        booking.put("bookingNumber", ++bookingId);
                        booking.put("address", get(row, "address"));
                        booking.put("image", get(row, "image"));
                        booking.put("capacity", get(row, "capacity"));
                        booking.put("bedrooms", get(row, "bedrooms"));
                        booking.put("distanceToLake", get(row, "lakeDist"));
                        booking.put("nearestCity", get(row, "city"));
                        booking.put("distanceToCity", get(row, "cityDist"));
                        booking.put("bookingStart", startDate);
                        booking.put("bookingEnd", endDate);

                        resultsArray.put(booking);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        JSONObject responseJson = new JSONObject();
        responseJson.put("bookings", resultsArray);

        out.print(responseJson.toString(2));
        out.flush();
    }

    // ======= 工具函数 =======
    private static int parseInt(String v, int def) {
        try { return Integer.parseInt(v); } catch (Exception e) { return def; }
    }
    private static float parseFloat(String v, float def) {
        try { return Float.parseFloat(v); } catch (Exception e) { return def; }
    }
    private static String get(BindingSet row, String name) {
        Value v = row.getValue(name);
        return v == null ? "" : v.stringValue();
    }

    private static List<String[]> generatePossiblePeriods(String startDateStr, int numDays, int shiftDays) {
        List<String[]> periods = new ArrayList<>();
        try {
            Date base = INPUT_FORMAT.parse(startDateStr);
            Calendar cal = Calendar.getInstance();

            for (int i = -shiftDays; i <= shiftDays; i++) {
                cal.setTime(base);
                cal.add(Calendar.DAY_OF_MONTH, i);
                Date start = cal.getTime();
                cal.add(Calendar.DAY_OF_MONTH, numDays);
                Date end = cal.getTime();

                periods.add(new String[]{
                    XSD_FORMAT.format(start), XSD_FORMAT.format(end)
                });
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return periods;
    }
}
