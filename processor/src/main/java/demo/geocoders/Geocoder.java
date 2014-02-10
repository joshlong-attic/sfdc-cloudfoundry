package demo.geocoders;


public interface Geocoder {


    LatLong geocode(String address) ;

     static class LatLong {
        private double latitude, longitude;

        public LatLong(double latitude, double longitude) {

            this.latitude = latitude;
            this.longitude = longitude;
        }

        @Override
        public String toString() {
            return " {" +
                    "latitude :" + latitude +
                    ", longitude : " + longitude +
                    '}';
        }

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }
    }
}
