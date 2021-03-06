/**
    This example is (C) Copyright 1999 Raja Vallee-Rai & Patrick Lam
    ...and is under the GNU LGPL.
 */

package ashes.tools.unitweigher;

 
import soot.*;
import soot.jimple.*;
import soot.grimp.*;
import java.io.*;
import java.util.*;
import soot.util.*;

/**
   Unit count instrumenter.  Instruments the classfiles so that the execution frequencies of all the 
   invokes is determined by site.  The general term unitweigher is used as opposed to invoke weigher
   because it will be generalized to all units at some indefinite point in the future.
 */
 
public class Main
{    
    public static void main(String[] args) 
    {
        if(args.length == 0)
        {
            System.out.println("Syntax: java ashes.tools.unitweigher.Main --app mainClass [soot options]");
            System.exit(0);
        }            
        
        Scene.v().getPack("wjtp").add(new Transform("wjtp.profiler", Profiler.v()));
        soot.Main.main(args);
    }
}


class Profiler extends SceneTransformer
{
    private static Profiler instance = new Profiler();
    private Profiler() {}
    static String oldPath;
    
    public static Profiler v() { return instance; }

    SootClass counterClass, systemClass;
    
    SootField countArray, isProfilingField;
    
    SootMethod systemExitMethod;
    
    HashMap invokeToNumber = new HashMap();
    HashMap numberToSignature = new HashMap();

    void processClass(SootClass sClass)
    {
        // Add code to increase counters
        {
            Iterator methodIt = sClass.getMethods().iterator();
            
            while(methodIt.hasNext())
            {
                SootMethod m = (SootMethod) methodIt.next();

                if(!m.isConcrete())
                    continue;
                
                JimpleBody body = (JimpleBody) m.retrieveActiveBody();
                                
                Local tmpLocal = Jimple.v().newLocal("tmp", LongType.v());
                Local isProfiling = Jimple.v().newLocal("isProfiling", IntType.v());
                Local tmpArray = Jimple.v().newLocal("tmpArray", ArrayType.v(LongType.v(), 1));
                body.getLocals().add(isProfiling);
                body.getLocals().add(tmpLocal);
                body.getLocals().add(tmpArray);

                Chain units = body.getUnits();
                Iterator stmtIt = units.snapshotIterator();
                                
                while(stmtIt.hasNext())
                {
                    Stmt s = (Stmt) stmtIt.next();

                    if(s.containsInvokeExpr())           
                    {
                        // Process this statement.
                        
                        InvokeExpr invokeExpr = (InvokeExpr) s.getInvokeExpr();                        

                        int invokeID = ((Integer)invokeToNumber.get(invokeExpr)).intValue();

                        List l = new ArrayList();
                        IfStmt toPatch;
                        
                        l.add(Jimple.v().newAssignStmt(isProfiling,
                                Jimple.v().newStaticFieldRef(isProfilingField)));
                        l.add(toPatch = Jimple.v().newIfStmt(Jimple.v().newEqExpr(isProfiling, IntConstant.v(0)), 
                            (Unit) null));
                                
                        l.add(Jimple.v().newAssignStmt(tmpArray, 
                                Jimple.v().newStaticFieldRef(countArray)));
                        l.add(Jimple.v().newAssignStmt(tmpLocal, 
                                Jimple.v().newArrayRef(tmpArray, IntConstant.v(invokeID))));
                        l.add(Jimple.v().newAssignStmt(tmpLocal,
                                Jimple.v().newAddExpr(tmpLocal, LongConstant.v(1L))));
                        l.add(Jimple.v().newAssignStmt(
                                Jimple.v().newArrayRef(tmpArray, IntConstant.v(invokeID)), 
                                tmpLocal));
                        units.insertBefore(l, s);
                        
                        toPatch.setTarget(s);
                        
                        if(invokeExpr.getMethod() == systemExitMethod)
                        {   
                            // Add a call to CounterClass before the System.exit(x) call
                                units.insertBefore(Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(
                                    counterClass.getMethod("void stopProfiling()"))), s);
                        }                                                                                        
                    }
                }
            }
        }
        

    }   
    
    protected void internalTransform(String phaseName, Map options)
    {        
        // Load counter counterClass
        {
            System.out.println("Locating CounterClass...");
            String oldPath = Scene.v().getSootClassPath();
            
            Scene.v().setSootClassPath("<external-class-path>");
            counterClass = Scene.v().loadClassAndSupport("ashes.tools.unitweigher.CounterClass");
            counterClass.setApplicationClass();        
            
            Scene.v().setSootClassPath(oldPath);
        }
                
        // Initialize some fields                        
            countArray = counterClass.getField("long[] unitCounts");
            isProfilingField = counterClass.getField("boolean isProfiling");
            
            systemClass = Scene.v().loadClassAndSupport("java.lang.System");
            systemExitMethod = systemClass.getMethod("void exit(int)");            

        int invokeNumber = 0;
        // Pre-process each class, constructing the invokeToNumberMap
        {
            Iterator classIt = Scene.v().getApplicationClasses().iterator();
            
            while(classIt.hasNext())
            {
                SootClass sClass = (SootClass) classIt.next();
                                       
                Iterator methodIt = sClass.getMethods().iterator();
                
                while(methodIt.hasNext())
                {
                    SootMethod m = (SootMethod) methodIt.next();

                     if(!m.isConcrete())
                        continue;
            
                    JimpleBody body = (JimpleBody) m.retrieveActiveBody();

                     Iterator unitsIt = body.getUnits().iterator();
                    int unitCount = 0;
                    while (unitsIt.hasNext())
                    {
                        Stmt s = (Stmt)unitsIt.next();
                        if (s.containsInvokeExpr())
                        {
                            InvokeExpr ie = (InvokeExpr)s.getInvokeExpr();
                            Integer nin = new Integer(invokeNumber);

                             invokeToNumber.put(ie, nin);
                            numberToSignature.put(nin, m.getSignature() + "+" + unitCount);
                            invokeNumber++;
                        }
                        unitCount++;
                    }
                }
            }
        }

        // Open file
            FileOutputStream streamOut = null;
            PrintWriter out = null;
            
            try {
                streamOut = new FileOutputStream("signatures.txt");
                out = new PrintWriter(streamOut);
            }
            catch (IOException e)
            {
                System.out.println("Cannot output sigfile");
            }
        
        // Write out profiling information
        {
            for (int i = 0; i < invokeNumber; i++)
                out.println(numberToSignature.get(new Integer(i)));
        }
        
        // Close file
            try     
            {   
                out.flush();
                streamOut.close();
            }
            catch (IOException e)
            {
                System.out.println("Cannot output file signatures.txt");
            }

        // Handle each class
        {
            Iterator classIt = Scene.v().getApplicationClasses().iterator();
            
            while(classIt.hasNext())
            {
                SootClass sClass = (SootClass) classIt.next();
                
                System.out.print("Inserting counters for " + sClass.getName() + "... " );
                System.out.flush();
                       
                processClass(sClass);
                System.out.println();
            }
        }

        // Create the unitCounts array
        {
            SootMethod initM = counterClass.getMethod("void startProfiling()");
            JimpleBody b = Jimple.v().newBody(initM);
            initM.setActiveBody(b);
            b.insertIdentityStmts();

            Local l = Jimple.v().newLocal("t", ArrayType.v(LongType.v(), 1));
            b.getLocals().add(l);

            List unitsToAdd = new ArrayList();

            unitsToAdd.add(Jimple.v().newAssignStmt(Jimple.v().newStaticFieldRef(isProfilingField), IntConstant.v(1)));
            unitsToAdd.add(Jimple.v().newAssignStmt(l, Jimple.v().newNewArrayExpr(LongType.v(), IntConstant.v(invokeNumber))));
            unitsToAdd.add(Jimple.v().newAssignStmt(Jimple.v().newStaticFieldRef(countArray), l));
            unitsToAdd.add(Jimple.v().newReturnVoidStmt());

            b.getUnits().addAll(unitsToAdd);
        }
        
        // Add a call to CounterClass.stopProfiling() before each return in the main method.
        {
            SootClass sClass = Scene.v().getMainClass();
            SootMethod m = sClass.getMethod("void main(java.lang.String[])");
                
            JimpleBody body = (JimpleBody) m.getActiveBody();
            
            Chain units = body.getUnits();

            Iterator stmtIt = units.snapshotIterator();
            
            while(stmtIt.hasNext())
            {
                Stmt s = (Stmt) stmtIt.next();
                
                if(s instanceof ReturnVoidStmt)
                {
                    units.insertBefore(Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(
                        counterClass.getMethod("void stopProfiling()"))), s);
                } 
            }
        }        

        // Add a call to CounterClass.startProfiling() in the main method.
        {
            SootClass sClass = Scene.v().getMainClass();
            SootMethod m = sClass.getMethod("void main(java.lang.String[])");
            JimpleBody body = (JimpleBody) m.getActiveBody();
            
            Chain units = body.getUnits();

            units.insertBefore(Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(
                counterClass.getMethod("void startProfiling()"))), body.getFirstNonIdentityStmt());
        }
    }
}










